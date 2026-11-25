package com.ravita.offer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchange compartilhado pelos serviços. A declaração é idempotente: os outros serviços declaram o
 * mesmo exchange com os mesmos parâmetros (durável, sem auto-delete), e quem subir primeiro cria.
 */
@Configuration
public class RabbitMQConfig {

    /** Fila do modelo de leitura (CQRS): recebe os eventos que o próprio offer-service publica. */
    public static final String READ_MODEL_QUEUE = "offer-service.read-model-queue";

    @Bean
    public TopicExchange ravitaExchange(@Value("${ravita.rabbitmq.exchange:ravita.events}") String name) {
        return new TopicExchange(name);
    }

    /**
     * Uma fila só para o serviço, não uma por réplica: com várias réplicas, cada evento é consumido por
     * uma delas e todas leem a mesma projeção no offer_db.
     */
    @Bean
    public Queue readModelQueue() {
        return new Queue(READ_MODEL_QUEUE, true);
    }

    @Bean
    public Binding readModelBinding(Queue readModelQueue, TopicExchange ravitaExchange) {
        return BindingBuilder.bind(readModelQueue).to(ravitaExchange).with("offer.#");
    }
}
