package com.ravita.offer.readmodel;

import com.ravita.offer.api.error.ApiException;
import com.ravita.offer.domain.FoodCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Lado de consulta do CQRS: lê só available_offers_read, nunca a tabela offers.
 * Os comandos (criar, reservar, liberar, expirar) continuam no OfferService e no ReservationService.
 */
@Service
public class AvailableOfferQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AvailableOfferReadRepository repository;
    private final Clock clock;

    public AvailableOfferQueryService(AvailableOfferReadRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Ofertas disponíveis, as que vencem antes primeiro; page começa em 1, como no resto do contrato. */
    @Transactional(readOnly = true)
    public Page<AvailableOfferRead> search(FoodCategory category, String city, Integer page, Integer size) {
        int pageNumber = page == null ? 1 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 1) {
            throw ApiException.invalidRequest("page", "deve ser maior ou igual a 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ApiException.invalidRequest("size", "deve estar entre 1 e " + MAX_PAGE_SIZE);
        }
        Sort sort = Sort.by(Sort.Order.asc("availableUntil"), Sort.Order.asc("offerId"));
        String cityFilter = city == null || city.isBlank() ? null : city;
        return repository.findAll(
                AvailableOfferSpecifications.available(category, cityFilter, clock.instant()),
                PageRequest.of(pageNumber - 1, pageSize, sort));
    }
}
