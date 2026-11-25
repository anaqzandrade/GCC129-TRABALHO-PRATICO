package com.ravita.offer.readmodel;

import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.OfferStatus;
import com.ravita.offer.domain.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Linha do modelo de leitura (Query Model do CQRS), tabela available_offers_read (migration V3).
 * Só é alterada pelo OfferProjectionConsumer, a partir dos eventos; nenhum comando escreve aqui.
 */
@Entity
@Table(name = "available_offers_read")
public class AvailableOfferRead {

    @Id
    @Column(name = "offer_id", length = 100)
    private String offerId;

    @Column(name = "donor_id", nullable = false, length = 100)
    private String donorId;

    @Column(name = "donor_name", length = 200)
    private String donorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_category", nullable = false, length = 30)
    private FoodCategory foodCategory;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Unit unit;

    @Column(length = 100)
    private String city;

    @Column(name = "city_normalized", length = 100)
    private String cityNormalized;

    @Column(name = "available_until", nullable = false)
    private Instant availableUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    /* Versão da oferta no último evento aplicado. Não é @Version: quem controla é o modelo de escrita. */
    @Column(name = "version", nullable = false)
    private long aggregateVersion;

    @Column(name = "last_event_type", nullable = false, length = 40)
    private String lastEventType;

    @Column(name = "event_occurred_at", nullable = false)
    private Instant eventOccurredAt;

    @Column(name = "projected_at", nullable = false)
    private Instant projectedAt;

    protected AvailableOfferRead() {
    }

    AvailableOfferRead(String offerId) {
        this.offerId = offerId;
    }

    /** Aplica o retrato da oferta que veio no evento. donorName ausente mantém o que já havia. */
    void apply(String donorId, String donorName, FoodCategory foodCategory, String description,
               BigDecimal quantity, Unit unit, String city, Instant availableUntil, OfferStatus status,
               long aggregateVersion, String eventType, Instant occurredAt, Instant now) {
        this.donorId = donorId;
        if (donorName != null) {
            this.donorName = donorName;
        }
        this.foodCategory = foodCategory;
        if (description != null) {
            this.description = description;
        }
        this.quantity = quantity;
        this.unit = unit;
        this.city = city;
        this.cityNormalized = Offer.normalizeCity(city);
        this.availableUntil = availableUntil;
        this.status = status;
        this.aggregateVersion = aggregateVersion;
        this.lastEventType = eventType;
        this.eventOccurredAt = occurredAt;
        this.projectedAt = now;
    }

    /**
     * Um evento mais antigo que o já aplicado não muda o estado, mas ainda pode trazer o nome do
     * doador (só o OfferCreated traz), caso o OfferCreated tenha chegado depois de outro evento.
     */
    void fillDonorNameIfMissing(String donorName) {
        if (this.donorName == null && donorName != null) {
            this.donorName = donorName;
        }
    }

    public String getOfferId() { return offerId; }
    public String getDonorId() { return donorId; }
    public String getDonorName() { return donorName; }
    public FoodCategory getFoodCategory() { return foodCategory; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public Unit getUnit() { return unit; }
    public String getCity() { return city; }
    public String getCityNormalized() { return cityNormalized; }
    public Instant getAvailableUntil() { return availableUntil; }
    public OfferStatus getStatus() { return status; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getLastEventType() { return lastEventType; }
    public Instant getEventOccurredAt() { return eventOccurredAt; }
    public Instant getProjectedAt() { return projectedAt; }
}
