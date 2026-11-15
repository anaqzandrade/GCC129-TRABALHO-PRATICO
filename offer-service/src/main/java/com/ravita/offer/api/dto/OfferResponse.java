package com.ravita.offer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.OfferStatus;
import com.ravita.offer.domain.Unit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** Offer do contrato: representação completa, para o dono e para serviços autorizados. */
public record OfferResponse(
        String id,
        String donorId,
        FoodCategory foodCategory,
        String description,
        BigDecimal quantity,
        Unit unit,
        OffsetDateTime availableFrom,
        OffsetDateTime availableUntil,
        AddressDto pickupAddress,
        OfferStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        @JsonProperty("_links") Map<String, LinkDto> links) {
}
