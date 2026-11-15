package com.ravita.offer.client;

import com.ravita.offer.api.CorrelationIdFilter;
import com.ravita.offer.api.error.ApiException;
import com.ravita.offer.config.RavitaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;

/**
 * Consulta o organization-service para validar o doador (comunicação síncrona REST).
 * Falhas viram os erros do contrato: indisponível → 503, timeout → 504.
 */
@Component
public class OrganizationClient {

    private static final Logger log = LoggerFactory.getLogger(OrganizationClient.class);

    private final RestClient restClient;

    public OrganizationClient(RestClient.Builder builder, RavitaProperties properties) {
        RavitaProperties.Services services = properties.services();
        Duration timeout = services.organizationTimeout() != null
                ? services.organizationTimeout() : Duration.ofSeconds(2);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());

        this.restClient = builder
                .baseUrl(services.organizationUrl())
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = CorrelationIdFilter.current();
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    /** Organização pelo id; vazio se o organization-service responder 404. */
    public Optional<OrganizationView> findById(String organizationId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/v1/organizations/{id}", organizationId)
                    .retrieve()
                    .body(OrganizationView.class));
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (RestClientResponseException responseError) {
            HttpStatusCode status = responseError.getStatusCode();
            log.warn("organization-service respondeu {} ao consultar {}", status.value(), organizationId);
            throw unavailable();
        } catch (ResourceAccessException ioError) {
            if (ioError.getCause() instanceof SocketTimeoutException) {
                log.warn("Timeout ao consultar organization-service");
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "DEPENDENCY_TIMEOUT",
                        "O serviço de organizações não respondeu a tempo.");
            }
            log.warn("organization-service inacessível: {}", ioError.getMessage());
            throw unavailable();
        } catch (RestClientException otherError) {
            log.warn("Resposta inválida do organization-service: {}", otherError.getMessage());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "O serviço de organizações está indisponível.");
    }
}
