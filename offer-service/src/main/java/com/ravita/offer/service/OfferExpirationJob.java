package com.ravita.offer.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marca periodicamente como EXPIRED as ofertas disponíveis cuja janela terminou.
 * Com várias réplicas todas rodam o job: cada oferta é expirada numa transação própria com @Version,
 * então só uma réplica grava a mudança e o evento OfferExpired; as outras pulam a oferta.
 */
@Component
public class OfferExpirationJob {

    private final OfferService offerService;

    public OfferExpirationJob(OfferService offerService) {
        this.offerService = offerService;
    }

    @Scheduled(fixedDelayString = "${ravita.expiration.interval-ms:60000}",
            initialDelayString = "${ravita.expiration.interval-ms:60000}")
    public void expireOverdueOffers() {
        offerService.expireOverdue();
    }
}
