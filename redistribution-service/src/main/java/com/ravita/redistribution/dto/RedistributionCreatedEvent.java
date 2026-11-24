package com.ravita.redistribution.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RedistributionCreatedEvent(
    String eventType,
    UUID redistributionId,
    String offerId,
    String institutionId,
    LocalDateTime timestamp
) {}
