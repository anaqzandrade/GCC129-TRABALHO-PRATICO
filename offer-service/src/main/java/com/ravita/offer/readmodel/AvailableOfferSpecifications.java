package com.ravita.offer.readmodel;

import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.OfferStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Filtros da consulta GET /offers/available, aplicados só ao modelo de leitura. */
public final class AvailableOfferSpecifications {

    private AvailableOfferSpecifications() {
    }

    /**
     * Status AVAILABLE e janela ainda aberta. A janela é conferida aqui porque o job de expiração roda
     * a cada minuto, e o evento OfferExpired pode ainda não ter chegado à projeção.
     */
    public static Specification<AvailableOfferRead> available(FoodCategory category, String city, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), OfferStatus.AVAILABLE));
            predicates.add(cb.greaterThan(root.<Instant>get("availableUntil"), now));
            if (category != null) {
                predicates.add(cb.equal(root.get("foodCategory"), category));
            }
            if (city != null) {
                predicates.add(cb.equal(root.get("cityNormalized"), Offer.normalizeCity(city)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
