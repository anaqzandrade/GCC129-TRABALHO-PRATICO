package com.ravita.offer.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marca periodicamente como EXPIRED as ofertas disponíveis cuja janela terminou.
 * Com várias réplicas todas rodam o job, mas o UPDATE é idempotente.
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
