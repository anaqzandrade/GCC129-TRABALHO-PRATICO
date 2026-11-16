package com.ravita.redistribution.dto;

import java.util.UUID;

public record CreateRedistributionRequest(
    UUID offerId,
    UUID institutionId,
    String pickupAddress,
    String deliveryAddress
) {}