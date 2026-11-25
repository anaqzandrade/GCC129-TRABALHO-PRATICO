package com.ravita.matching.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ravita.matching.config.RabbitMQConfig;
import com.ravita.matching.model.AvailableOffer;
import com.ravita.matching.model.OfferStatus;
import com.ravita.matching.model.FoodCategory;
import com.ravita.matching.model.ProcessedEvent;
import com.ravita.matching.model.Unit;
import com.ravita.matching.repository.AvailableOfferRepository;
import com.ravita.matching.repository.ProcessedEventRepository;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
public class OfferEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OfferEventConsumer.class);

    @Autowired
    private AvailableOfferRepository offerRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    /**
     * Consumidor idempotente (doc 03, seção 9). O outbox do offer-service entrega cada evento pelo menos
     * uma vez: se o offer cair entre publicar e marcar PUBLISHED, o mesmo eventId chega de novo.
     *
     * <p>O efeito do evento e o registro em processed_events são gravados na mesma transação: ou os dois
     * ficam, ou nenhum. Se a transação falhar, o RabbitMQ entrega a mensagem de novo e ela é reprocessada
     * do zero; se der certo, uma nova entrega do mesmo eventId é ignorada.
     */
    @Transactional
    @RabbitListener(queues = RabbitMQConfig.OFFER_EVENTS_QUEUE)
    public void handleOfferEvent(OfferEventMessage event) {
        if (event == null || event.getEventType() == null) return;

        String eventId = event.getEventId();
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.info("Evento repetido ignorado: {} da oferta {} (eventId {})",
                    event.getEventType(), event.getAggregateId(), eventId);
            return;
        }

        boolean applied = apply(event);

        if (eventId != null) {
            processedEventRepository.save(new ProcessedEvent(eventId, event.getEventType(), OffsetDateTime.now()));
        } else {
            log.warn("Evento {} da oferta {} sem eventId: aplicado sem controle de repetição",
                    event.getEventType(), event.getAggregateId());
        }
        if (applied) {
            log.info("Evento {} da oferta {} aplicado (eventId {})",
                    event.getEventType(), event.getAggregateId(), eventId);
        }
    }

    /** Devolve false para tipos de evento que o matching não usa. */
    private boolean apply(OfferEventMessage event) {
        switch (event.getEventType()) {
            case "OfferCreated":
            case "OfferUpdated":
                saveOrUpdateOffer(event);
                return true;
            case "OfferReserved":
            case "OfferReleased":
            case "OfferCancelled":
            case "OfferExpired":
                updateOfferStatus(event);
                return true;
            default:
                return false;
        }
    }

    private void saveOrUpdateOffer(OfferEventMessage event) {
        if (event.getData() == null) return;
        
        AvailableOffer offer = offerRepository.findById(event.getAggregateId())
                .orElse(new AvailableOffer());
                
        offer.setOfferId(event.getAggregateId());
        offer.setDonorId(event.getData().getDonorId());
        offer.setFoodCategory(event.getData().getFoodCategory());
        offer.setQuantity(event.getData().getQuantity());
        offer.setUnit(event.getData().getUnit());
        offer.setCity(event.getData().getCity());
        offer.setStatus(OfferStatus.AVAILABLE);
        offer.setAvailableUntil(event.getData().getAvailableUntil());
        
        offerRepository.save(offer);
    }

    private void updateOfferStatus(OfferEventMessage event) {
        offerRepository.findById(event.getAggregateId()).ifPresent(offer -> {
            if ("OfferReserved".equals(event.getEventType())) {
                offer.setStatus(OfferStatus.RESERVED);
            } else if ("OfferReleased".equals(event.getEventType())) {
                // compensação da SAGA: a reserva foi liberada e a oferta volta a ser compatível
                offer.setStatus(OfferStatus.AVAILABLE);
            } else if ("OfferCancelled".equals(event.getEventType())) {
                offer.setStatus(OfferStatus.CANCELLED);
            } else if ("OfferExpired".equals(event.getEventType())) {
                offer.setStatus(OfferStatus.EXPIRED);
            }
            offerRepository.save(offer);
        });
    }
}

// Estruturas auxiliares para mapear o JSON do evento do RabbitMQ.
// ignoreUnknown: o offer-service manda campos a mais (aggregateVersion, donorName, status...)
// que o matching não usa; eles são ignorados em vez de derrubar a desserialização.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class OfferEventMessage {
    private String eventId;
    private String eventType;
    private OffsetDateTime occurredAt;
    private String aggregateId;
    private OfferEventData data;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class OfferEventData {
    private String donorId;
    private FoodCategory foodCategory;
    private BigDecimal quantity;
    private Unit unit;
    private String city;
    private OffsetDateTime availableUntil;
}