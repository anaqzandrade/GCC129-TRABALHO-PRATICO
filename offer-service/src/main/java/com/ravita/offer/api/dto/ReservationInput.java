package com.ravita.offer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /offers/{offerId}/reservations. */
public record ReservationInput(@NotBlank @Size(max = 100) String redistributionId) {

    public ReservationInput {
        redistributionId = Text.trim(redistributionId);
    }
}
