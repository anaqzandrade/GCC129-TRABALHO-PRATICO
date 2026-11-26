package com.ravita.redistribution.controller;

import com.ravita.redistribution.dto.CreateRedistributionRequest;
import com.ravita.redistribution.dto.PageResponse;
import com.ravita.redistribution.dto.RedistributionStatistics;
import com.ravita.redistribution.model.Redistribution;
import com.ravita.redistribution.model.RedistributionStatus;
import com.ravita.redistribution.service.RedistributionSagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** API interna do orquestrador, no prefixo /api/v1 do contrato. Os clientes chegam aqui pelos BFFs. */
@RestController
@RequestMapping("/api/v1/redistributions")
@RequiredArgsConstructor
public class RedistributionController {

    private final RedistributionSagaService sagaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Redistribution create(@RequestBody CreateRedistributionRequest request,
                                 @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return sagaService.createAndStartSaga(request, correlationId);
    }

    /** Listagem paginada (page começa em 1), mais recentes primeiro. Filtros opcionais: status e instituição. */
    @GetMapping
    public PageResponse<Redistribution> list(
            @RequestParam(required = false) RedistributionStatus status,
            @RequestParam(required = false) String institutionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Redistribution> result = sagaService.list(status, institutionId, page, size);
        return new PageResponse<>(result.getContent(), result.getNumber() + 1, result.getSize(),
                result.getTotalElements());
    }

    /** Literal "statistics" tem precedência sobre /{id}. */
    @GetMapping("/statistics")
    public RedistributionStatistics statistics() {
        return sagaService.statistics();
    }

    @GetMapping("/{id}")
    public Redistribution getById(@PathVariable UUID id) {
        return sagaService.getById(id);
    }

    @PostMapping("/{id}/cancel")
    public Redistribution cancel(@PathVariable UUID id) {
        return sagaService.cancel(id);
    }
}
