package com.ravita.redistribution.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Chamadas síncronas da SAGA aos serviços participantes, seguindo o contrato REST
 * (docs/arquitetura/02-contratos-rest.md). Todas têm timeout: um participante fora do ar
 * gera erro em segundos, e não uma requisição presa.
 */
@Component
public class ServiceClients {

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final RestClient offerClient;
    private final RestClient logisticsClient;

    public ServiceClients(
            @Value("${ravita.services.offer-url}") String offerUrl,
            @Value("${ravita.services.logistics-url}") String logisticsUrl) {
        this.offerClient = RestClient.builder().baseUrl(offerUrl).requestFactory(timeouts()).build();
        this.logisticsClient = RestClient.builder().baseUrl(logisticsUrl).requestFactory(timeouts()).build();
    }

    /**
     * T2 - ReserveOffer: POST /api/v1/offers/{offerId}/reservations.
     * O redistributionId torna o comando idempotente: repetir devolve a mesma reserva.
     *
     * @return id da reserva criada no offer-service
     */
    public String reserveOffer(String offerId, UUID redistributionId, String correlationId) {
        Map<?, ?> reservation = offerClient.post()
                .uri("/api/v1/offers/{offerId}/reservations", offerId)
                .headers(headers -> correlation(headers, correlationId))
                .body(Map.of("redistributionId", redistributionId.toString()))
                .retrieve()
                .body(Map.class);
        return reservation.get("id").toString();
    }

    /**
     * C2 - ReleaseOffer (compensação de T2): DELETE /api/v1/offers/{offerId}/reservations/{reservationId}.
     * Liberar uma reserva já liberada também responde 204.
     */
    public void releaseOffer(String offerId, String reservationId, String correlationId) {
        offerClient.delete()
                .uri("/api/v1/offers/{offerId}/reservations/{reservationId}", offerId, reservationId)
                .headers(headers -> correlation(headers, correlationId))
                .retrieve()
                .toBodilessEntity();
    }

    /** T3 - CreateLogistics: POST /api/v1/logistics. */
    public UUID createLogistics(UUID redistributionId, String offerId, String pickup, String delivery,
                                String correlationId) {
        Map<String, Object> body = Map.of(
                "redistributionId", redistributionId,
                "offerId", offerId,
                "pickupAddress", pickup != null ? pickup : "Origem",
                "deliveryAddress", delivery != null ? delivery : "Destino"
        );

        Map<?, ?> response = logisticsClient.post()
                .uri("/api/v1/logistics")
                .headers(headers -> correlation(headers, correlationId))
                .body(body)
                .retrieve()
                .body(Map.class);

        return UUID.fromString(response.get("id").toString());
    }

    /** Repassa o X-Correlation-ID da SAGA: os logs dos três serviços ficam com o mesmo id. */
    private static void correlation(HttpHeaders headers, String correlationId) {
        if (correlationId != null) {
            headers.set("X-Correlation-ID", correlationId);
        }
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }
}
