package com.ravita.admin.config;

import com.ravita.admin.web.BffException;
import com.ravita.admin.web.CorrelationIdFilter;
import com.ravita.admin.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Clientes HTTP dos serviços que o Admin BFF usa. Todos têm timeout e repassam o X-Correlation-ID
 * e a identidade (X-User-Id, X-Org-Id, X-Roles) recebida do gateway.
 * O BFF consulta as APIs dos serviços, nunca os bancos (doc 02, seção 13).
 */
@Component
public class ServiceClients {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final List<String> PROPAGATED = List.of(CurrentUser.USER_ID, CurrentUser.ORG_ID, CurrentUser.ROLES);

    private final RestClient offer;
    private final RestClient organization;
    private final RestClient redistribution;
    private final RestClient logistics;

    public ServiceClients(@Value("${ravita.services.offer-url}") String offerUrl,
                          @Value("${ravita.services.organization-url}") String organizationUrl,
                          @Value("${ravita.services.redistribution-url}") String redistributionUrl,
                          @Value("${ravita.services.logistics-url}") String logisticsUrl) {
        this.offer = client(offerUrl);
        this.organization = client(organizationUrl);
        this.redistribution = client(redistributionUrl);
        this.logistics = client(logisticsUrl);
    }

    public RestClient offer() { return offer; }
    public RestClient organization() { return organization; }
    public RestClient redistribution() { return redistribution; }
    public RestClient logistics() { return logistics; }

    /**
     * Executa a chamada; serviço inacessível vira 503 e serviço que não responde a tempo vira 504.
     * Um BFF nunca transforma falha de consulta em lista vazia (doc 02, seção 3).
     */
    public <T> T call(String service, Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException ioError) {
            throw failure(service, ioError);
        }
    }

    /**
     * Conexão recusada, nome que não resolve e conexão que não abre no prazo significam que o serviço não
     * está lá: 503. Com o container parado, a JVM ainda guarda o IP antigo no cache de DNS (30 s), e a
     * tentativa termina em "Connect timed out", não em nome desconhecido. Só o serviço que aceitou a
     * conexão e não respondeu dentro do prazo de leitura é um 504.
     */
    static BffException failure(String service, ResourceAccessException ioError) {
        Throwable cause = ioError.getCause();
        boolean readTimeout = cause instanceof SocketTimeoutException
                && !String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT).contains("connect");
        if (readTimeout) {
            return new BffException(HttpStatus.GATEWAY_TIMEOUT, "DEPENDENCY_TIMEOUT",
                    "O " + service + " não respondeu a tempo.");
        }
        return new BffException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "O " + service + " está indisponível.");
    }

    /** Igual a call, mas um 404 do serviço vira null. */
    public <T> T findOrNull(String service, Supplier<T> request) {
        try {
            return call(service, request);
        } catch (HttpClientErrorException.NotFound notFound) {
            return null;
        }
    }

    private static RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(ServiceClients::propagateHeaders)
                .build();
    }

    private static ClientHttpResponse propagateHeaders(HttpRequest request, byte[] body,
                                                       ClientHttpRequestExecution execution) throws IOException {
        if (CorrelationIdFilter.current() != null) {
            request.getHeaders().set(CorrelationIdFilter.HEADER, CorrelationIdFilter.current());
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest incoming = attributes.getRequest();
            for (String name : PROPAGATED) {
                String value = incoming.getHeader(name);
                if (value != null) {
                    request.getHeaders().set(name, value);
                }
            }
        }
        return execution.execute(request, body);
    }
}
