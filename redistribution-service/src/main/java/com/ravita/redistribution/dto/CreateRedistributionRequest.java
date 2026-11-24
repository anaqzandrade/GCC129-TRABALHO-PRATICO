package com.ravita.redistribution.dto;

public record CreateRedistributionRequest(
    String offerId,
    String institutionId,
    String pickupAddress,
    String deliveryAddress
) {}
