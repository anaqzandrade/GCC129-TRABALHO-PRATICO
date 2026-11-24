package com.ravita.logistics.dto;

import java.util.UUID;

public record CreateLogisticsRequest(
    UUID redistributionId,
    String offerId,
    String pickupAddress,
    String deliveryAddress
) {}