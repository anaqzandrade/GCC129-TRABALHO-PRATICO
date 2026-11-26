package com.ravita.offer.outbox;

/** Estado de uma linha do outbox. */
public enum OutboxStatus {
    /** Gravada junto com a mudança da oferta, ainda não confirmada pelo RabbitMQ. */
    PENDING,
    /** O RabbitMQ confirmou o recebimento (publisher confirm). */
    PUBLISHED,
    /** Desistiu depois de ravita.outbox.max-attempts erros que não eram de conexão. */
    FAILED
}
