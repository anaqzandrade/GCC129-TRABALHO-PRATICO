package com.ravita.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Oferta de doação: um lote de alimento disponível para retirada numa janela de tempo.
 * Fonte autoritativa do estado da oferta (Command Model do CQRS).
 */
@Entity
@Table(name = "offers")
public class Offer {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "donor_id", nullable = false, length = 100)
    private String donorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_category", nullable = false, length = 30)
    private FoodCategory foodCategory;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Unit unit;

    @Column(name = "available_from", nullable = false)
    private Instant availableFrom;

    @Column(name = "available_until", nullable = false)
    private Instant availableUntil;

    @Embedded
    private Address pickupAddress;

    @Column(name = "pickup_city_normalized", nullable = false, length = 100)
    private String pickupCityNormalized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    /**
     * Controle otimista de concorrência: se duas reservas tentam alterar a mesma versão,
     * só a primeira grava; a segunda recebe 409. Também é a base do ETag ("v3").
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Offer() {
    }

    public static Offer create(String donorId, FoodCategory foodCategory, String description,
                               BigDecimal quantity, Unit unit, Instant availableFrom,
                               Instant availableUntil, Address pickupAddress, Instant now) {
        Offer offer = new Offer();
        offer.id = "offer-" + UUID.randomUUID();
        offer.donorId = donorId;
        offer.foodCategory = foodCategory;
        offer.description = description;
        offer.quantity = quantity;
        offer.unit = unit;
        offer.availableFrom = availableFrom;
        offer.availableUntil = availableUntil;
        offer.pickupAddress = pickupAddress;
        offer.pickupCityNormalized = normalizeCity(pickupAddress.getCity());
        offer.status = OfferStatus.AVAILABLE;
        offer.createdAt = now;
        offer.updatedAt = now;
        return offer;
    }

    /** Disponível e dentro da janela: única situação em que a oferta pode ser reservada. */
    public boolean isReservable(Instant now) {
        return status == OfferStatus.AVAILABLE && !isExpiredAt(now);
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(availableUntil);
    }

    /** A coleta já aconteceu: liberar a reserva não desfaz mais nada. */
    public boolean isCollected() {
        return status == OfferStatus.COLLECTED || status == OfferStatus.COMPLETED;
    }

    public void markReserved(Instant now) {
        this.status = OfferStatus.RESERVED;
        this.updatedAt = now;
    }

    /** Compensação da SAGA: volta a AVAILABLE se ainda estiver na janela; senão, vence. */
    public void markReleased(Instant now) {
        if (status != OfferStatus.RESERVED) {
            return;
        }
        this.status = isExpiredAt(now) ? OfferStatus.EXPIRED : OfferStatus.AVAILABLE;
        this.updatedAt = now;
    }

    /**
     * ExpireOffer: só vence uma oferta disponível cuja janela já terminou.
     * Devolve false quando não há nada a fazer (outra réplica ou uma reserva mudou a oferta antes).
     */
    public boolean markExpired(Instant now) {
        if (status != OfferStatus.AVAILABLE || !isExpiredAt(now)) {
            return false;
        }
        this.status = OfferStatus.EXPIRED;
        this.updatedAt = now;
        return true;
    }

    /** "São João del-Rei " → "sao joao del-rei", para o filtro por cidade ignorar acento e caixa. */
    public static String normalizeCity(String city) {
        if (city == null) {
            return null;
        }
        String withoutAccents = Normalizer.normalize(city.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT);
    }

    public String getId() { return id; }
    public String getDonorId() { return donorId; }
    public FoodCategory getFoodCategory() { return foodCategory; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public Unit getUnit() { return unit; }
    public Instant getAvailableFrom() { return availableFrom; }
    public Instant getAvailableUntil() { return availableUntil; }
    public Address getPickupAddress() { return pickupAddress; }
    public OfferStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
