package com.ravita.logistics.dto;

import java.util.UUID;

public record CreateLogisticsRequest(
    UUID redistributionId,
    UUID offerId,
    String pickupAddress,
    String deliveryAddress
) {}