package com.ravita.offer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.OfferStatus;
import com.ravita.offer.domain.Unit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** OfferSummary do contrato: usado nas listagens; não expõe o endereço completo, só cidade e UF. */
public record OfferSummaryResponse(
        String id,
        String donorId,
        FoodCategory foodCategory,
        String description,
        BigDecimal quantity,
        Unit unit,
        OffsetDateTime availableFrom,
        OffsetDateTime availableUntil,
        OfferStatus status,
        String city,
        String state,
        OffsetDateTime createdAt,
        @JsonProperty("_links") Map<String, LinkDto> links) {
}
