package com.ravita.offer.repository;

import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, String>, JpaSpecificationExecutor<Offer> {

    /**
     * Marca como EXPIRED as ofertas disponíveis cuja janela terminou.
     * Incrementa a versão para que um PATCH ou reserva concorrente perceba a mudança.
     * É idempotente: várias réplicas podem rodar ao mesmo tempo sem efeito duplicado.
     */
    @Modifying
    @Query("""
            update Offer o
               set o.status = com.ravita.offer.domain.OfferStatus.EXPIRED,
                   o.version = o.version + 1,
                   o.updatedAt = :now
             where o.status = com.ravita.offer.domain.OfferStatus.AVAILABLE
               and o.availableUntil <= :now
            """)
    int expireOverdue(@Param("now") Instant now);

    @Query("select o.status as status, count(o) as total from Offer o group by o.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        OfferStatus getStatus();

        long getTotal();
    }
}
