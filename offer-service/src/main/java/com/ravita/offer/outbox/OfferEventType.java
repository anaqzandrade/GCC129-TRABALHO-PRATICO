package com.ravita.offer.outbox;

/**
 * Eventos de domínio do offer-service (doc 03, seção 10) e a routing key de cada um
 * no exchange topic ravita.events. Os consumidores se ligam com "offer.#".
 */
public enum OfferEventType {

    OFFER_CREATED("OfferCreated", "offer.created"),
    OFFER_RESERVED("OfferReserved", "offer.reserved"),
    /** Compensação da SAGA: a reserva foi liberada e a oferta voltou a AVAILABLE. */
    OFFER_RELEASED("OfferReleased", "offer.released"),
    OFFER_EXPIRED("OfferExpired", "offer.expired");

    private final String eventName;
    private final String routingKey;

    OfferEventType(String eventName, String routingKey) {
        this.eventName = eventName;
        this.routingKey = routingKey;
    }

    /** Nome que vai no campo eventType do JSON, por exemplo "OfferCreated". */
    public String eventName() {
        return eventName;
    }

    public String routingKey() {
        return routingKey;
    }
}
