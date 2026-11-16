package com.ravita.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class MatchesResponse {
    private String demandId;
    private List<MatchDto> matches;
    private OffsetDateTime generatedAt;
}