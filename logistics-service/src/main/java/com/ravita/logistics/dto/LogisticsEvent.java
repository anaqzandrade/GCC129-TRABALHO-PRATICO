package com.ravita.logistics.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LogisticsEvent(
    String eventType,
    UUID logisticsId,
    UUID redistributionId,
    UUID offerId,
    LocalDateTime timestamp
) {}