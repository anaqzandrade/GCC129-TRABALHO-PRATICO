package com.ravita.offer.api;

import com.ravita.offer.api.dto.OfferInput;
import com.ravita.offer.api.dto.OfferResponse;
import com.ravita.offer.api.dto.OfferSummaryResponse;
import com.ravita.offer.api.dto.PageResponse;
import com.ravita.offer.api.dto.StatisticsResponse;
import com.ravita.offer.api.error.ApiException;
import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.OfferStatus;
import com.ravita.offer.service.OfferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Endpoints de domínio do Offer Service (contrato, seção 3). Não são publicados pelo Gateway:
 * os clientes chegam aqui pelos BFFs (/portal/v1 e /admin/v1).
 */
@RestController
@RequestMapping(OfferMapper.BASE_PATH)
public class OfferController {

    private static final Set<String> LIST_PARAMS = Set.of("page", "size", "status", "category", "city", "donorId");

    private final OfferService offerService;
    private final OfferMapper mapper;
    private final Clock clock;
    private final ZoneId responseZone;

    public OfferController(OfferService offerService, OfferMapper mapper, Clock clock, ZoneId responseZone) {
        this.offerService = offerService;
        this.mapper = mapper;
        this.clock = clock;
        this.responseZone = responseZone;
    }

    @PostMapping
    public ResponseEntity<OfferResponse> create(@Valid @RequestBody OfferInput input) {
        Offer offer = offerService.create(input);
        return ResponseEntity.created(URI.create(OfferMapper.offerPath(offer.getId())))
                .eTag(OfferMapper.etag(offer))
                .body(mapper.toResponse(offer));
    }

    @GetMapping
    public PageResponse<OfferSummaryResponse> list(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) OfferStatus status,
            @RequestParam(required = false) FoodCategory category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String donorId) {
        rejectUnknownParams(request);
        Page<Offer> result = offerService.list(status, category, city, donorId, page, size);
        return new PageResponse<>(
                result.getContent().stream().map(mapper::toSummary).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements());
    }

    /** Literal "statistics" tem precedência sobre /{offerId} no Spring MVC. */
    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        Map<String, Long> counts = new LinkedHashMap<>();
        offerService.countByStatus().forEach((status, total) -> counts.put(status.name(), total));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new StatisticsResponse(total, counts, clock.instant().atZone(responseZone).toOffsetDateTime());
    }

    @GetMapping("/{offerId}")
    public ResponseEntity<OfferResponse> get(@PathVariable String offerId) {
        Offer offer = offerService.get(offerId);
        return ResponseEntity.ok()
                .eTag(OfferMapper.etag(offer))
                .body(mapper.toResponse(offer));
    }

    /** Contrato: filtros desconhecidos retornam 400. */
    private static void rejectUnknownParams(HttpServletRequest request) {
        for (String name : Collections.list(request.getParameterNames())) {
            if (!LIST_PARAMS.contains(name)) {
                throw ApiException.invalidRequest(name, "filtro desconhecido");
            }
        }
    }
}
