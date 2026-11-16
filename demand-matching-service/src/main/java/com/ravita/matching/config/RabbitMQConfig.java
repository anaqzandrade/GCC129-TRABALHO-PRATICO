package com.ravita.matching.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara a fila que o OfferEventConsumer escuta e a liga à exchange de eventos do sistema.
 * Sem esta declaração a fila não existe no RabbitMQ e o listener falha ao iniciar.
 */
@Configuration
public class RabbitMQConfig {

    public static final String OFFER_EVENTS_QUEUE = "matching-service.offer-events-queue";

    /** Todos os eventos do Offer Service: offer.created, offer.reserved, offer.released... */
    public static final String OFFER_EVENTS_ROUTING_KEY = "offer.#";

    @Value("${ravita.rabbitmq.exchange:ravita.events}")
    private String exchangeName;

    /** Mesma declaração do redistribution e do logistics (topic, durável), para não conflitar. */
    @Bean
    public TopicExchange ravitaExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue offerEventsQueue() {
        return new Queue(OFFER_EVENTS_QUEUE, true);
    }

    @Bean
    public Binding bindOfferEvents(Queue offerEventsQueue, TopicExchange ravitaExchange) {
        return BindingBuilder.bind(offerEventsQueue).to(ravitaExchange).with(OFFER_EVENTS_ROUTING_KEY);
    }

    /** Os eventos chegam em JSON; o listener recebe direto o OfferEventMessage. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
