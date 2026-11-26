package com.ravita.offer.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpAuthenticationException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.List;

/**
 * Publica no RabbitMQ as linhas PENDING do outbox (doc 03, seção 7).
 *
 * <p>Entrega pelo menos uma vez: a linha só vira PUBLISHED depois da confirmação do broker
 * (publisher confirm). Se o serviço cair entre a confirmação e o commit, a linha é publicada de novo,
 * por isso os consumidores ignoram eventId repetido.
 *
 * <p>Com o RabbitMQ fora do ar, nada se perde: as linhas continuam PENDING e saem quando ele voltar.
 * Falha de conexão não conta como tentativa, porque o problema não é do evento. Só um erro do próprio
 * envio (por exemplo, o broker recusar a mensagem) conta; depois de max-attempts, a linha fica FAILED.
 */
@Component
@ConditionalOnProperty(prefix = "ravita.outbox", name = "publisher-enabled", havingValue = "true",
        matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;
    private final String exchange;
    private final int batchSize;
    private final int maxAttempts;

    /** Só para não repetir o mesmo aviso a cada segundo enquanto o RabbitMQ está fora. */
    private volatile boolean brokerDown;

    public OutboxPublisher(OutboxRepository repository, RabbitTemplate rabbitTemplate, Clock clock,
                           @Value("${ravita.rabbitmq.exchange:ravita.events}") String exchange,
                           @Value("${ravita.outbox.batch-size:50}") int batchSize,
                           @Value("${ravita.outbox.max-attempts:10}") int maxAttempts) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
        this.exchange = exchange;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${ravita.outbox.interval-ms:1000}",
            initialDelayString = "${ravita.outbox.interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextPending(batchSize);
        for (OutboxEvent event : batch) {
            try {
                publish(event);
                event.markPublished(clock.instant());
                if (brokerDown) {
                    brokerDown = false;
                    log.info("RabbitMQ respondeu de novo; retomando a publicação do outbox");
                }
                log.info("Evento {} da oferta {} publicado ({})",
                        event.getEventType(), event.getAggregateId(), event.getEventId());
            } catch (AmqpConnectException | AmqpIOException | AmqpTimeoutException
                     | AmqpAuthenticationException brokerUnavailable) {
                // conexão recusada, nome "rabbitmq" que nem resolve (container parado), timeout ou
                // credencial errada: o problema é o broker, não o evento, então não conta tentativa
                if (!brokerDown) {
                    brokerDown = true;
                    log.warn("RabbitMQ indisponível ({}); os eventos aguardam no outbox até ele voltar",
                            brokerUnavailable.getClass().getSimpleName());
                }
                // as próximas também falhariam: para aqui e tenta de novo no próximo ciclo
                return;
            } catch (AmqpException publishError) {
                event.registerFailure(maxAttempts);
                log.warn("Falha ao publicar o evento {} (tentativa {}): {}",
                        event.getEventId(), event.getAttemptCount(), publishError.getMessage());
            }
        }
    }

    /** Envia e espera a confirmação do RabbitMQ antes de considerar o evento publicado. */
    private void publish(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        // JSON puro, sem o cabeçalho __TypeId__: cada consumidor desserializa na própria classe
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(event.getEventId());
        properties.setType(event.getEventType());
        properties.setTimestamp(Date.from(event.getCreatedAt()));
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);

        rabbitTemplate.invoke(operations -> {
            operations.send(exchange, event.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
