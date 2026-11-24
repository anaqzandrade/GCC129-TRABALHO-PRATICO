package com.ravita.redistribution.controller;

import com.ravita.redistribution.dto.CreateRedistributionRequest;
import com.ravita.redistribution.model.Redistribution;
import com.ravita.redistribution.service.RedistributionSagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/redistributions")
@RequiredArgsConstructor
public class RedistributionController {

    private final RedistributionSagaService sagaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Redistribution create(@RequestBody CreateRedistributionRequest request) {
        return sagaService.createAndStartSaga(request);
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