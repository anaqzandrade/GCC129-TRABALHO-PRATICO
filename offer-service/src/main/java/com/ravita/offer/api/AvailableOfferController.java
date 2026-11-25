package com.ravita.offer.api;

import com.ravita.offer.api.dto.AvailableOfferResponse;
import com.ravita.offer.api.dto.PageResponse;
import com.ravita.offer.api.error.ApiException;
import com.ravita.offer.domain.FoodCategory;
import com.ravita.offer.readmodel.AvailableOfferQueryService;
import com.ravita.offer.readmodel.AvailableOfferRead;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Set;

/**
 * Consulta do CQRS: GET /api/v1/offers/available lê só o modelo de leitura (available_offers_read).
 * Pode estar até alguns segundos atrás do modelo de escrita; a reserva sempre é validada no modelo de
 * escrita, então uma oferta que ainda aparece aqui como disponível não pode ser reservada duas vezes.
 */
@RestController
@RequestMapping(OfferMapper.BASE_PATH)
public class AvailableOfferController {

    private static final Set<String> PARAMS = Set.of("page", "size", "category", "city");

    private final AvailableOfferQueryService queryService;
    private final OfferMapper mapper;

    public AvailableOfferController(AvailableOfferQueryService queryService, OfferMapper mapper) {
        this.queryService = queryService;
        this.mapper = mapper;
    }

    /** Literal "available" tem precedência sobre /{offerId} no Spring MVC. */
    @GetMapping("/available")
    public PageResponse<AvailableOfferResponse> available(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) FoodCategory category,
            @RequestParam(required = false) String city) {
        for (String name : Collections.list(request.getParameterNames())) {
            if (!PARAMS.contains(name)) {
                throw ApiException.invalidRequest(name, "filtro desconhecido");
            }
        }
        Page<AvailableOfferRead> result = queryService.search(category, city, page, size);
        return new PageResponse<>(
                result.getContent().stream().map(mapper::toAvailable).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements());
    }
}
