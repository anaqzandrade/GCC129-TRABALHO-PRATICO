package com.ravita.offer.repository;

import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, String>, JpaSpecificationExecutor<Offer> {

    /**
     * Ids das ofertas disponíveis cuja janela terminou. Cada uma é expirada na sua própria transação
     * (OfferService.expireOverdue), para gerar o evento OfferExpired de cada oferta no outbox.
     */
    @Query("""
            select o.id from Offer o
             where o.status = com.ravita.offer.domain.OfferStatus.AVAILABLE
               and o.availableUntil <= :now
             order by o.availableUntil
            """)
    List<String> findOverdueIds(@Param("now") Instant now);

    @Query("select o.status as status, count(o) as total from Offer o group by o.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        OfferStatus getStatus();

        long getTotal();
    }
}
