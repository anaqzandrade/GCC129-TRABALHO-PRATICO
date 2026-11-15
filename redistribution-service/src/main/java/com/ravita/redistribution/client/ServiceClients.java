package com.ravita.redistribution.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class ServiceClients {

    private final RestClient offerClient;
    private final RestClient logisticsClient;

    public ServiceClients(
            @Value("${ravita.services.offer-url}") String offerUrl,
            @Value("${ravita.services.logistics-url}") String logisticsUrl) {
        this.offerClient = RestClient.builder().baseUrl(offerUrl).build();
        this.logisticsClient = RestClient.builder().baseUrl(logisticsUrl).build();
    }

    public void reserveOffer(UUID offerId) {
        offerClient.post()
                .uri("/offers/{id}/reserve", offerId)
                .retrieve()
                .toBodilessEntity();
    }

    public void releaseOffer(UUID offerId) {
        offerClient.post()
                .uri("/offers/{id}/release", offerId)
                .retrieve()
                .toBodilessEntity();
    }

    public UUID createLogistics(UUID redistributionId, UUID offerId, String pickup, String delivery) {
        Map<String, Object> body = Map.of(
                "redistributionId", redistributionId,
                "offerId", offerId,
                "pickupAddress", pickup != null ? pickup : "Origem",
                "deliveryAddress", delivery != null ? delivery : "Destino"
        );

        Map response = logisticsClient.post()
                .uri("/logistics")
                .body(body)
                .retrieve()
                .body(Map.class);

        return UUID.fromString(response.get("id").toString());
    }
}