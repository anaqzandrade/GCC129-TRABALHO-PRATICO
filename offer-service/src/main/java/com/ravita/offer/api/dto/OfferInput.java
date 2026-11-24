package com.ravita.offer.api.dto;

import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.Unit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * OfferInput do contrato. Erros de estrutura viram 400; regras de domínio
 * (quantidade, janela, doador) são verificadas no OfferService e viram 422.
 * pickupAddress é opcional: se ausente, copia o endereço atual do doador.
 */
public record OfferInput(
        @NotBlank @Size(max = 100) String donorId,
        @NotNull FoodCategory foodCategory,
        @NotBlank @Size(max = 1000) String description,
        @NotNull BigDecimal quantity,
        @NotNull Unit unit,
        @NotNull OffsetDateTime availableFrom,
        @NotNull OffsetDateTime availableUntil,
        @Valid AddressDto pickupAddress) {

    public OfferInput {
        donorId = Text.trim(donorId);
        description = Text.trim(description);
    }
}
