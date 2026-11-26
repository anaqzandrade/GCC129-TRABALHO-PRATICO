package com.ravita.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primeiro filtro de toda requisição, antes até da autenticação (doc 02, seção 3):
 * usa o X-Correlation-ID enviado pelo cliente ou gera um, repassa ao BFF, devolve na resposta
 * e grava uma linha de log por requisição com método, caminho, status, duração e o id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-ID";
    private static final String ATTRIBUTE = CorrelationIdWebFilter.class.getName();
    private static final int MAX_LENGTH = 100;
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String received = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = received == null || received.isBlank() || received.length() > MAX_LENGTH
                ? UUID.randomUUID().toString()
                : received.strip();

        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.header(HEADER, correlationId))
                .build();
        mutated.getAttributes().put(ATTRIBUTE, correlationId);
        mutated.getResponse().getHeaders().set(HEADER, correlationId);

        long start = System.nanoTime();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();
        return chain.filter(mutated).doFinally(signal -> {
            HttpStatusCode status = mutated.getResponse().getStatusCode();
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} ({} ms) [{}]", method, path, status == null ? "-" : status.value(), millis,
                    correlationId);
        });
    }

    /** O id desta requisição, para os erros escritos pelo gateway. */
    public static String correlationId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(ATTRIBUTE);
        return value != null ? value.toString() : exchange.getRequest().getHeaders().getFirst(HEADER);
    }
}
