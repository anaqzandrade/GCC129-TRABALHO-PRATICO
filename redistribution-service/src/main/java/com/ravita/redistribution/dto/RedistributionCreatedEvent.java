package com.ravita.redistribution.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RedistributionCreatedEvent(
    String eventType,
    UUID redistributionId,
    UUID offerId,
    UUID institutionId,
    LocalDateTime timestamp
) {}