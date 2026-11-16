package com.ravita.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class MatchDto {
    private String offerId;
    private String compatibility; // Ex: "HIGH"
}