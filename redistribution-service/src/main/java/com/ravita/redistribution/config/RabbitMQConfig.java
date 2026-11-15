package com.ravita.redistribution.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${ravita.rabbitmq.exchange}")
    private String exchangeName;

    public static final String COLLECTED_QUEUE = "redistribution.food.collected.queue";
    public static final String DELIVERED_QUEUE = "redistribution.food.delivered.queue";

    @Bean
    public TopicExchange ravitaExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Queue collectedQueue() {
        return new Queue(COLLECTED_QUEUE, true);
    }

    @Bean
    public Queue deliveredQueue() {
        return new Queue(DELIVERED_QUEUE, true);
    }

    @Bean
    public Binding bindCollected(Queue collectedQueue, TopicExchange ravitaExchange) {
        return BindingBuilder.bind(collectedQueue).to(ravitaExchange).with("logistics.food.collected");
    }

    @Bean
    public Binding bindDelivered(Queue deliveredQueue, TopicExchange ravitaExchange) {
        return BindingBuilder.bind(deliveredQueue).to(ravitaExchange).with("logistics.food.delivered");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}