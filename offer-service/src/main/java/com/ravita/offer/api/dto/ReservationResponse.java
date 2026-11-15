package com.ravita.offer.api.dto;

import com.ravita.offer.domain.ReservationStatus;

import java.time.OffsetDateTime;

/** Reservation do contrato. releasedAt só aparece quando a reserva foi liberada. */
public record ReservationResponse(
        String id,
        String offerId,
        String redistributionId,
        ReservationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime releasedAt) {
}
