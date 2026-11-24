package com.ravita.offer.service;

import com.ravita.offer.api.dto.OfferInput;
import com.ravita.offer.api.error.ApiException;
import com.ravita.offer.client.OrganizationClient;
import com.ravita.offer.client.OrganizationView;
import com.ravita.offer.domain.Address;
import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.OfferStatus;
import com.ravita.offer.domain.Unit;
import com.ravita.offer.repository.OfferRepository;
import com.ravita.offer.repository.OfferSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999.999");
    private static final int MAX_PAGE_SIZE = 100;

    private final OfferRepository offerRepository;
    private final OrganizationClient organizationClient;
    private final Clock clock;

    public OfferService(OfferRepository offerRepository, OrganizationClient organizationClient, Clock clock) {
        this.offerRepository = offerRepository;
        this.organizationClient = organizationClient;
        this.clock = clock;
    }

    /**
     * CreateOffer. Sem @Transactional de propósito: a chamada HTTP ao organization-service
     * acontece antes de abrir a transação, para não segurar uma conexão do banco esperando a rede.
     */
    public Offer create(OfferInput input) {
        Instant now = clock.instant();
        validateQuantity(input.quantity(), input.unit());
        Instant from = input.availableFrom().toInstant();
        Instant until = input.availableUntil().toInstant();
        validateWindow(from, until, now);

        OrganizationView donor = organizationClient.findById(input.donorId())
                .filter(OrganizationView::isActiveDonor)
                .orElseThrow(() -> ApiException.businessRule("INVALID_DONOR",
                        "O doador não existe, não está ativo ou não é do tipo DONOR."));

        Address pickupAddress = input.pickupAddress() != null
                ? input.pickupAddress().toDomain()
                : copyDonorAddress(donor);

        Offer offer = Offer.create(input.donorId(), input.foodCategory(), input.description(),
                input.quantity(), input.unit(), from, until, pickupAddress, now);
        Offer saved = offerRepository.save(offer);
        log.info("Oferta {} criada pelo doador {}", saved.getId(), saved.getDonorId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Offer get(String offerId) {
        return offerRepository.findById(offerId).orElseThrow(ApiException::notFound);
    }

    /** GET /offers: ordenação fixa createdAt DESC, id ASC; page começa em 1. */
    @Transactional(readOnly = true)
    public Page<Offer> list(OfferStatus status, FoodCategory category, String city, String donorId,
                            Integer page, Integer size) {
        int pageNumber = page == null ? 1 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 1) {
            throw ApiException.invalidRequest("page", "deve ser maior ou igual a 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ApiException.invalidRequest("size", "deve estar entre 1 e " + MAX_PAGE_SIZE);
        }
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        PageRequest pageable = PageRequest.of(pageNumber - 1, pageSize, sort);
        String cityFilter = city == null || city.isBlank() ? null : city;
        String donorFilter = donorId == null || donorId.isBlank() ? null : donorId.strip();
        return offerRepository.findAll(
                OfferSpecifications.withFilters(status, category, cityFilter, donorFilter, clock.instant()),
                pageable);
    }

    /** Contagem por estado para o dashboard do Admin BFF; estados sem ofertas aparecem com zero. */
    @Transactional(readOnly = true)
    public Map<OfferStatus, Long> countByStatus() {
        Map<OfferStatus, Long> counts = new EnumMap<>(OfferStatus.class);
        for (OfferStatus status : OfferStatus.values()) {
            counts.put(status, 0L);
        }
        offerRepository.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    /** ExpireOffer em lote, chamado pelo OfferExpirationJob. */
    @Transactional
    public int expireOverdue() {
        int expired = offerRepository.expireOverdue(clock.instant());
        if (expired > 0) {
            log.info("{} oferta(s) marcada(s) como EXPIRED", expired);
        }
        return expired;
    }

    /** Quantity do contrato: positivo, até 3 casas, máximo 999999999.999; UNIT exige inteiro. */
    static void validateQuantity(BigDecimal quantity, Unit unit) {
        BigDecimal normalized = quantity.stripTrailingZeros();
        if (quantity.signum() <= 0) {
            throw invalidQuantity("A quantidade deve ser positiva.");
        }
        if (normalized.scale() > 3) {
            throw invalidQuantity("A quantidade aceita no máximo três casas decimais.");
        }
        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw invalidQuantity("A quantidade máxima é 999999999.999.");
        }
        if (unit == Unit.UNIT && normalized.scale() > 0) {
            throw invalidQuantity("Para a unidade UNIT a quantidade deve ser inteira.");
        }
    }

    /** A janela precisa terminar depois de começar e ainda não pode ter terminado. */
    static void validateWindow(Instant from, Instant until, Instant now) {
        if (!from.isBefore(until)) {
            throw ApiException.businessRule("INVALID_WINDOW", "availableFrom deve ser anterior a availableUntil.");
        }
        if (!now.isBefore(until)) {
            throw ApiException.businessRule("INVALID_WINDOW", "A janela de disponibilidade já terminou.");
        }
    }

    private static ApiException invalidQuantity(String message) {
        return ApiException.businessRule("INVALID_QUANTITY", message);
    }

    private static Address copyDonorAddress(OrganizationView donor) {
        OrganizationView.OrganizationAddress address = donor.address();
        if (address == null || address.street() == null || address.city() == null) {
            throw ApiException.businessRule("INVALID_DONOR",
                    "O doador não tem endereço cadastrado; informe pickupAddress.");
        }
        return new Address(address.street(), address.number(), address.complement(), address.district(),
                address.city(), address.state(), address.postalCode());
    }
}
