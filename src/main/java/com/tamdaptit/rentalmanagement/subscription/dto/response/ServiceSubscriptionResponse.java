package com.tamdaptit.rentalmanagement.subscription.dto.response;

import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.subscription.entity.enums.SubscriptionStatus;

public record ServiceSubscriptionResponse(
    String id,
    String packageId,
    SubscriptionStatus status,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime createdAt
) {}
