package com.ravita.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Erros do próprio gateway no formato do contrato. O principal: BFF fora do ar vira 503
 * SERVICE_UNAVAILABLE (doc 02, seção 3), e não o 500 genérico do Spring Cloud Gateway.
 * Ordem -2: roda antes do tratador padrão do Spring Boot (-1).
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (causedBy(error, ConnectException.class) || causedBy(error, UnknownHostException.class)) {
            log.warn("Destino indisponível para {}: {}", path, error.getMessage());
            return JsonErrors.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "O serviço de destino está indisponível.");
        }
        if (causedBy(error, TimeoutException.class)) {
            log.warn("Destino não respondeu a tempo para {}", path);
            return JsonErrors.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT",
                    "O serviço de destino não respondeu a tempo.");
        }
        if (error instanceof ResponseStatusException statusError) {
            HttpStatus status = HttpStatus.resolve(statusError.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            String message = statusError.getReason() != null ? statusError.getReason() : status.getReasonPhrase();
            return JsonErrors.write(exchange, status, status.name(), message);
        }
        log.error("Erro inesperado no gateway em {}", path, error);
        return JsonErrors.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Erro inesperado no gateway.");
    }

    private static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
