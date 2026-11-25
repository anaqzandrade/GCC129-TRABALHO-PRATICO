package com.ravita.redistribution.dto;

import java.util.Map;

/** Contagem de redistribuições por status, para o dashboard do Admin BFF. */
public record RedistributionStatistics(long total, Map<String, Long> byStatus) {
}
