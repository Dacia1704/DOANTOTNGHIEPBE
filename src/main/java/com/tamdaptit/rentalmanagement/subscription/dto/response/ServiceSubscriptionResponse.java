package com.tamdaptit.rentalmanagement.subscription.dto.response;

import com.tamdaptit.rentalmanagement.subscription.entity.enums.SubscriptionStatus;
import java.time.LocalDateTime;

public record ServiceSubscriptionResponse(
        String id, String packageId, SubscriptionStatus status,
        LocalDateTime startAt, LocalDateTime endAt, LocalDateTime createdAt) {
}
