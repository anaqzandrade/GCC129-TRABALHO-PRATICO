package com.ravita.offer.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** Statistics do contrato (seção 7): soma de counts == total; inclui estados com zero. */
public record StatisticsResponse(long total, Map<String, Long> counts, OffsetDateTime asOf) {
}
