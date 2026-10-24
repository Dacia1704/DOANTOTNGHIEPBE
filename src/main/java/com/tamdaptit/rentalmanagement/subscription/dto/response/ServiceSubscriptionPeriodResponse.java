package com.tamdaptit.rentalmanagement.subscription.dto.response;

import java.time.LocalDateTime;

public record ServiceSubscriptionPeriodResponse(
        String id, String subscriptionId, String orderId, Integer periodNumber,
        LocalDateTime startAt, LocalDateTime endAt, SubscriptionRightsResponse rights) {
}
