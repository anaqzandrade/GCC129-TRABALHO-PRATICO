package com.ravita.matching.consumer;

import com.ravita.matching.model.AvailableOffer;
import com.ravita.matching.model.OfferStatus;
import com.ravita.matching.model.FoodCategory;
import com.ravita.matching.model.Unit;
import com.ravita.matching.repository.AvailableOfferRepository;
import lombok.Data;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
public class OfferEventConsumer {

    @Autowired
    private AvailableOfferRepository offerRepository;

    @RabbitListener(queues = "matching-service.offer-events-queue")
    public void handleOfferEvent(OfferEventMessage event) {
        if (event == null || event.getEventType() == null) return;

        switch (event.getEventType()) {
            case "OfferCreated":
            case "OfferUpdated":
                saveOrUpdateOffer(event);
                break;
            case "OfferReserved":
            case "OfferCancelled":
            case "OfferExpired":
                updateOfferStatus(event);
                break;
            default:
                break;
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
            } else if ("OfferCancelled".equals(event.getEventType())) {
                offer.setStatus(OfferStatus.CANCELLED);
            } else if ("OfferExpired".equals(event.getEventType())) {
                offer.setStatus(OfferStatus.EXPIRED);
            }
            offerRepository.save(offer);
        });
    }
}

// Estruturas auxiliares para mapear o JSON do evento do RabbitMQ
@Data
class OfferEventMessage {
    private String eventId;
    private String eventType;
    private OffsetDateTime occurredAt;
    private String aggregateId;
    private OfferEventData data;
}

@Data
class OfferEventData {
    private String donorId;
    private FoodCategory foodCategory;
    private BigDecimal quantity;
    private Unit unit;
    private String city;
    private OffsetDateTime availableUntil;
}