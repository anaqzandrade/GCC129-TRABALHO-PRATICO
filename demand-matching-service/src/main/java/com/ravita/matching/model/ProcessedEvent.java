package com.ravita.matching.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Evento de oferta já aplicado (doc 03, seção 9). O outbox do offer-service entrega cada evento
 * pelo menos uma vez; esta tabela permite reconhecer e ignorar uma entrega repetida.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", length = 40)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String eventType, OffsetDateTime processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
}
