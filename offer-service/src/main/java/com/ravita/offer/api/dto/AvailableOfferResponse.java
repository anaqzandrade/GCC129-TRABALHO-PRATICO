package com.ravita.offer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.OfferStatus;
import com.ravita.offer.domain.Unit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Item de GET /offers/available, montado a partir do modelo de leitura.
 * lastEvent, eventOccurredAt e projectedAt mostram de qual evento veio o dado e com quanta defasagem.
 */
public record AvailableOfferResponse(
        String id,
        String donorId,
        String donorName,
        FoodCategory foodCategory,
        String description,
        BigDecimal quantity,
        Unit unit,
        String city,
        OffsetDateTime availableUntil,
        OfferStatus status,
        String lastEvent,
        OffsetDateTime eventOccurredAt,
        OffsetDateTime projectedAt,
        @JsonProperty("_links") Map<String, LinkDto> links) {
}
