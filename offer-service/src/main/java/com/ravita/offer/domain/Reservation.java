package com.ravita.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserva de uma oferta por uma redistribuição (passo T2 da SAGA).
 * Cada reserva envolve o lote inteiro. Liberar a reserva é a compensação do passo.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "offer_id", nullable = false, length = 100)
    private String offerId;

    @Column(name = "redistribution_id", nullable = false, length = 100, unique = true)
    private String redistributionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected Reservation() {
    }

    public static Reservation create(String offerId, String redistributionId, Instant now) {
        Reservation reservation = new Reservation();
        reservation.id = "res-" + UUID.randomUUID();
        reservation.offerId = offerId;
        reservation.redistributionId = redistributionId;
        reservation.status = ReservationStatus.ACTIVE;
        reservation.createdAt = now;
        return reservation;
    }

    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }

    public void release(Instant now) {
        if (status == ReservationStatus.RELEASED) {
            return;
        }
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = now;
    }

    public String getId() { return id; }
    public String getOfferId() { return offerId; }
    public String getRedistributionId() { return redistributionId; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReleasedAt() { return releasedAt; }
}
