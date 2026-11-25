package com.ravita.offer.outbox;

import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.OfferStatus;
import com.ravita.offer.domain.Unit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JSON publicado no RabbitMQ, no formato do doc 03 (seção 10), que o demand-matching já consome:
 * eventId, eventType, occurredAt, aggregateId e data.
 *
 * <p>Campos acrescentados para o modelo de leitura do CQRS (Etapa 4): aggregateVersion, que permite
 * ignorar um evento mais antigo que o já aplicado, e data.description, data.donorName e data.status.
 * Consumidores que não conhecem esses campos simplesmente os ignoram.
 */
public record OfferEventMessage(
        String eventId,
        String eventType,
        OffsetDateTime occurredAt,
        String aggregateId,
        Long aggregateVersion,
        Data data) {

    public record Data(
            String donorId,
            /* só no OfferCreated: o nome vem da consulta ao organization-service feita na criação */
            String donorName,
            FoodCategory foodCategory,
            String description,
            BigDecimal quantity,
            Unit unit,
            String city,
            OffsetDateTime availableUntil,
            OfferStatus status) {
    }
}
