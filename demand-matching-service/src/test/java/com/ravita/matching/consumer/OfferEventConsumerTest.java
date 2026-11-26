package com.ravita.matching.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravita.matching.model.AvailableOffer;
import com.ravita.matching.model.FoodCategory;
import com.ravita.matching.model.OfferStatus;
import com.ravita.matching.model.Unit;
import com.ravita.matching.repository.AvailableOfferRepository;
import com.ravita.matching.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Consumidor idempotente dos eventos de oferta, sem Spring nem RabbitMQ. */
@ExtendWith(MockitoExtension.class)
class OfferEventConsumerTest {

    @Mock
    private AvailableOfferRepository offerRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private OfferEventConsumer consumer;

    @Test
    void appliesANewEventAndRecordsItsId() {
        consumer.handleOfferEvent(event("evt-1", "OfferCreated"));

        verify(offerRepository).save(argThat(offer ->
                offer.getStatus() == OfferStatus.AVAILABLE && "Lavras".equals(offer.getCity())));
        verify(processedEventRepository).save(argThat(processed -> "evt-1".equals(processed.getEventId())));
    }

    @Test
    void ignoresAnEventThatWasAlreadyProcessed() {
        when(processedEventRepository.existsById("evt-1")).thenReturn(true);

        consumer.handleOfferEvent(event("evt-1", "OfferCreated"));

        verifyNoInteractions(offerRepository);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void releasedOfferBecomesAvailableAgain() {
        AvailableOffer reserved = new AvailableOffer();
        reserved.setOfferId("offer-1");
        reserved.setStatus(OfferStatus.RESERVED);
        when(offerRepository.findById("offer-1")).thenReturn(Optional.of(reserved));

        consumer.handleOfferEvent(event("evt-2", "OfferReleased"));

        assertThat(reserved.getStatus()).isEqualTo(OfferStatus.AVAILABLE);
        verify(offerRepository).save(reserved);
    }

    @Test
    void eventWithoutIdIsAppliedButNotRecorded() {
        consumer.handleOfferEvent(event(null, "OfferCreated"));

        verify(offerRepository).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void readsTheEventPublishedByTheOfferOutboxIgnoringExtraFields() throws Exception {
        String json = """
                {
                  "eventId": "evt-3",
                  "eventType": "OfferCreated",
                  "occurredAt": "2026-10-20T12:00:00-03:00",
                  "aggregateId": "offer-1",
                  "aggregateVersion": 0,
                  "data": {
                    "donorId": "org-donor",
                    "donorName": "Mercado Central",
                    "foodCategory": "VEGETABLES",
                    "description": "Hortaliças variadas",
                    "quantity": 30,
                    "unit": "KG",
                    "city": "Lavras",
                    "availableUntil": "2026-10-20T17:00:00-03:00",
                    "status": "AVAILABLE"
                  }
                }
                """;

        OfferEventMessage event = new ObjectMapper().findAndRegisterModules().readValue(json, OfferEventMessage.class);

        assertThat(event.getEventId()).isEqualTo("evt-3");
        assertThat(event.getData().getFoodCategory()).isEqualTo(FoodCategory.VEGETABLES);
        assertThat(event.getData().getQuantity()).isEqualByComparingTo("30");
        assertThat(event.getData().getAvailableUntil()).isEqualTo(OffsetDateTime.parse("2026-10-20T17:00:00-03:00"));
    }

    private static OfferEventMessage event(String eventId, String eventType) {
        OfferEventData data = new OfferEventData();
        data.setDonorId("org-donor");
        data.setFoodCategory(FoodCategory.VEGETABLES);
        data.setQuantity(new BigDecimal("30"));
        data.setUnit(Unit.KG);
        data.setCity("Lavras");
        data.setAvailableUntil(OffsetDateTime.parse("2026-10-20T17:00:00-03:00"));

        OfferEventMessage message = new OfferEventMessage();
        message.setEventId(eventId);
        message.setEventType(eventType);
        message.setAggregateId("offer-1");
        message.setData(data);
        return message;
    }
}
