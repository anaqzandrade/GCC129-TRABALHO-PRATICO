package com.ravita.offer.config;

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

    @Bean
    public TopicExchange ravitaExchange(@Value("${ravita.rabbitmq.exchange:ravita.events}") String name) {
        return new TopicExchange(name);
    }
}
