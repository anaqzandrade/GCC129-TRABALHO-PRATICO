package com.ravita.logistics.controller;

import com.ravita.logistics.dto.CreateLogisticsRequest;
import com.ravita.logistics.dto.PageResponse;
import com.ravita.logistics.model.LogisticsOperation;
import com.ravita.logistics.model.LogisticsStatus;
import com.ravita.logistics.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** API interna da logística, no prefixo /api/v1 do contrato. Chamada pela SAGA e pelos BFFs. */
@RestController
@RequestMapping("/api/v1/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LogisticsOperation create(@RequestBody CreateLogisticsRequest request) {
        return service.create(request);
    }

    /** Listagem paginada (page começa em 1), mais recentes primeiro. Filtros opcionais: status e redistribuição. */
    @GetMapping
    public PageResponse<LogisticsOperation> list(
            @RequestParam(required = false) LogisticsStatus status,
            @RequestParam(required = false) UUID redistributionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<LogisticsOperation> result = service.list(status, redistributionId, page, size);
        return new PageResponse<>(result.getContent(), result.getNumber() + 1, result.getSize(),
                result.getTotalElements());
    }

    @GetMapping("/{id}")
    public LogisticsOperation getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/collect")
    public LogisticsOperation collect(@PathVariable UUID id) {
        return service.collect(id);
    }

    @PostMapping("/{id}/deliver")
    public LogisticsOperation deliver(@PathVariable UUID id) {
        return service.deliver(id);
    }
}