package com.ravita.offer.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Evento já aplicado à projeção (tabela read_model_processed_events, migration V3). */
@Entity
@Table(name = "read_model_processed_events")
public class ProcessedReadEvent {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedReadEvent() {
    }

    ProcessedReadEvent(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public String getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}
