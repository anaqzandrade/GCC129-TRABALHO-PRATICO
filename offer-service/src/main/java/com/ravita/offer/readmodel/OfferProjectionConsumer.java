package com.ravita.offer.readmodel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.ravita.offer.config.RabbitMQConfig;
import com.ravita.offer.domain.OfferStatus;
import com.ravita.offer.outbox.OfferEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

/**
 * Atualiza o modelo de leitura a partir dos eventos de oferta (doc 03, seção 14).
 *
 * <p>Fluxo: comando grava em offers + outbox_events → OutboxPublisher publica em ravita.events →
 * fila offer-service.read-model-queue → este consumidor → available_offers_read.
 *
 * <p>Cada evento traz o retrato completo da oferta depois da mudança (data.status, quantidade, janela...),
 * então aplicar um evento é substituir a linha pelo retrato, desde que ele seja mais novo que o já
 * aplicado (aggregateVersion). Isso torna o consumidor idempotente e tolerante a eventos fora de ordem,
 * o que acontece quando várias réplicas do offer-service consomem a mesma fila.
 */
@Component
public class OfferProjectionConsumer {

    private static final Logger log = LoggerFactory.getLogger(OfferProjectionConsumer.class);

    private final AvailableOfferReadRepository readRepository;
    private final ProcessedReadEventRepository processedRepository;
    private final ObjectReader eventReader;
    private final Clock clock;

    public OfferProjectionConsumer(AvailableOfferReadRepository readRepository,
                                   ProcessedReadEventRepository processedRepository,
                                   ObjectMapper objectMapper, Clock clock) {
        this.readRepository = readRepository;
        this.processedRepository = processedRepository;
        // A API recusa campos desconhecidos (contrato), mas um evento pode ganhar campos novos
        // sem quebrar quem o consome.
        this.eventReader = objectMapper.readerFor(OfferEventMessage.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.clock = clock;
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.READ_MODEL_QUEUE)
    public void onOfferEvent(Message message) {
        OfferEventMessage event;
        try {
            event = eventReader.readValue(message.getBody());
        } catch (IOException invalidJson) {
            // mensagem que nunca vai ser lida: descarta em vez de voltar para a fila para sempre
            throw new AmqpRejectAndDontRequeueException("Evento de oferta com JSON inválido", invalidJson);
        }
        project(event);
    }

    /** Aplica um evento à projeção. Devolve false quando o evento não muda nada. */
    @Transactional
    public boolean project(OfferEventMessage event) {
        String eventId = event.eventId();
        if (eventId != null && processedRepository.existsById(eventId)) {
            log.info("Projeção: evento repetido ignorado ({} {})", event.eventType(), eventId);
            return false;
        }
        OfferEventMessage.Data data = event.data();
        OfferStatus status = data == null ? null : statusAfter(event.eventType(), data.status());
        if (event.aggregateId() == null || status == null || !isComplete(data)) {
            log.warn("Projeção: evento {} incompleto (sem oferta, estado ou dados obrigatórios); ignorado",
                    event.eventType());
            return false;
        }

        Instant now = clock.instant();
        long version = event.aggregateVersion() == null ? 0 : event.aggregateVersion();
        AvailableOfferRead row = readRepository.findById(event.aggregateId()).orElse(null);
        boolean applied;
        if (row != null && version <= row.getAggregateVersion()) {
            row.fillDonorNameIfMissing(data.donorName());
            log.info("Projeção: {} da oferta {} é mais antigo que o já aplicado (v{} <= v{}); ignorado",
                    event.eventType(), event.aggregateId(), version, row.getAggregateVersion());
            applied = false;
        } else {
            if (row == null) {
                row = new AvailableOfferRead(event.aggregateId());
            }
            Instant occurredAt = event.occurredAt() == null ? now : event.occurredAt().toInstant();
            row.apply(data.donorId(), data.donorName(), data.foodCategory(), data.description(),
                    data.quantity(), data.unit(), data.city(), data.availableUntil().toInstant(),
                    status, version, event.eventType(), occurredAt, now);
            readRepository.save(row);
            log.info("Projeção: oferta {} agora {} (v{}, {})", event.aggregateId(), status, version,
                    event.eventType());
            applied = true;
        }
        if (eventId != null) {
            processedRepository.save(new ProcessedReadEvent(eventId, now));
        }
        return applied;
    }

    /** O retrato precisa dos campos obrigatórios da tabela; description e donorName são opcionais. */
    private static boolean isComplete(OfferEventMessage.Data data) {
        return data.donorId() != null && data.foodCategory() != null && data.quantity() != null
                && data.unit() != null && data.availableUntil() != null;
    }

    /** O evento traz o estado em data.status; sem ele (evento publicado à mão), deduz pelo tipo. */
    private static OfferStatus statusAfter(String eventType, OfferStatus fromEvent) {
        if (fromEvent != null) {
            return fromEvent;
        }
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "OfferCreated", "OfferUpdated", "OfferReleased" -> OfferStatus.AVAILABLE;
            case "OfferReserved" -> OfferStatus.RESERVED;
            case "OfferExpired" -> OfferStatus.EXPIRED;
            case "OfferCancelled" -> OfferStatus.CANCELLED;
            default -> null;
        };
    }
}
