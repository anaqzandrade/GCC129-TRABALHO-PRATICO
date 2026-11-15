package com.ravita.offer.repository;

import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.OfferStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros de GET /offers. Todos combinam por AND; filtro ausente não restringe.
 */
public final class OfferSpecifications {

    private OfferSpecifications() {
    }

    public static Specification<Offer> withFilters(OfferStatus status, FoodCategory category,
                                                   String city, String donorId, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
                if (status == OfferStatus.AVAILABLE) {
                    // "disponível" também exige que a janela ainda não tenha terminado
                    predicates.add(cb.greaterThan(root.<Instant>get("availableUntil"), now));
                }
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("foodCategory"), category));
            }
            if (city != null) {
                predicates.add(cb.equal(root.get("pickupCityNormalized"), Offer.normalizeCity(city)));
            }
            if (donorId != null) {
                predicates.add(cb.equal(root.get("donorId"), donorId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
