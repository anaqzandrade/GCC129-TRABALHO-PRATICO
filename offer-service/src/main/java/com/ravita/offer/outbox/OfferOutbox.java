package com.ravita.offer.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravita.offer.domain.Offer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Grava o evento de uma mudança da oferta na tabela outbox_events.
 *
 * <p>Propagation.MANDATORY: só pode ser chamado dentro de uma transação já aberta, a mesma que altera
 * a oferta. Se alguém chamar fora de uma transação, o Spring lança exceção em vez de gravar o evento
 * separado da oferta, que é exatamente o dual write que o outbox existe para evitar.
 */
@Component
public class OfferOutbox {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId responseZone;

    public OfferOutbox(OutboxRepository repository, ObjectMapper objectMapper, Clock clock, ZoneId responseZone) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.responseZone = responseZone;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(Offer offer, OfferEventType type) {
        return record(offer, type, null);
    }

    /** donorName: só no OfferCreated, quando a criação já consultou o organization-service. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(Offer offer, OfferEventType type, String donorName) {
        Instant now = clock.instant();
        String eventId = UUID.randomUUID().toString();
        OfferEventMessage message = new OfferEventMessage(
                eventId,
                type.eventName(),
                now.atZone(responseZone).toOffsetDateTime(),
                offer.getId(),
                offer.getVersion(),
                new OfferEventMessage.Data(
                        offer.getDonorId(),
                        donorName,
                        offer.getFoodCategory(),
                        offer.getDescription(),
                        offer.getQuantity(),
                        offer.getUnit(),
                        offer.getPickupAddress().getCity(),
                        offer.getAvailableUntil().atZone(responseZone).toOffsetDateTime(),
                        offer.getStatus()));
        return repository.save(OutboxEvent.pending(eventId, offer.getId(), type, toJson(message), now));
    }

    private String toJson(OfferEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o evento " + message.eventType(), e);
        }
    }
}
