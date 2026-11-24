package com.ravita.matching.controller;

import com.ravita.matching.dto.DemandInput;
import com.ravita.matching.dto.MatchesResponse;
import com.ravita.matching.model.Demand;
import com.ravita.matching.service.DemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/demands")
public class DemandController {

    @Autowired
    private DemandService demandService;

    @PostMapping
    public ResponseEntity<Demand> createDemand(
            @RequestBody DemandInput input, 
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Demand demand = demandService.createDemand(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(demand);
    }

    @GetMapping
    public ResponseEntity<Page<Demand>> listDemands(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String institutionId) {
        // Nota: Pageable do Spring usa index 0 para a página, por isso subtraímos 1 se page >= 1
        int pageIndex = Math.max(0, page - 1);
        Page<Demand> demands = demandService.listDemands(pageIndex, size, institutionId);
        return ResponseEntity.ok(demands);
    }

    @GetMapping("/{demandId}")
    public ResponseEntity<Demand> getDemandById(@PathVariable String demandId) {
        Demand demand = demandService.findById(demandId);
        return ResponseEntity.ok(demand);
    }

    @GetMapping("/{demandId}/matches")
    public ResponseEntity<MatchesResponse> getMatches(@PathVariable String demandId) {
        MatchesResponse matches = demandService.findMatchesForDemand(demandId);
        return ResponseEntity.ok(matches);
    }
}