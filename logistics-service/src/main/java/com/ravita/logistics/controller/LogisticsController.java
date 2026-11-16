package com.ravita.logistics.controller;

import com.ravita.logistics.dto.CreateLogisticsRequest;
import com.ravita.logistics.model.LogisticsOperation;
import com.ravita.logistics.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LogisticsOperation create(@RequestBody CreateLogisticsRequest request) {
        return service.create(request);
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