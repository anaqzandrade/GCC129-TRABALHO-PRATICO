package com.ravita.offer.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Linha da tabela outbox_events (migration V2). */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 60)
    private String routingKey;

    /* TEXT no PostgreSQL (migration V2) */
    @Column(nullable = false, length = 10000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    static OutboxEvent pending(String eventId, String aggregateId, OfferEventType type, String payload,
                               Instant now) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = eventId;
        event.aggregateType = "Offer";
        event.aggregateId = aggregateId;
        event.eventType = type.eventName();
        event.routingKey = type.routingKey();
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.attemptCount = 0;
        event.createdAt = now;
        return event;
    }

    void markPublished(Instant now) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
    }

    /** Erro que não é de conexão (por exemplo, o broker recusou a mensagem). */
    void registerFailure(int maxAttempts) {
        this.attemptCount++;
        if (this.attemptCount >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        }
    }

    public String getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
