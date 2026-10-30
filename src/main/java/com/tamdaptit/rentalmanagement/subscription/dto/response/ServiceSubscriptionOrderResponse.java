package com.tamdaptit.rentalmanagement.subscription.dto.response;

import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderStatus;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceSubscriptionOrderResponse(
        String id, String landlordId, String packageId, String priceOptionId,
        String subscriptionId, String previousSubscriptionId,
        ServiceSubscriptionOrderType type, ServiceSubscriptionOrderStatus status,
        Integer durationMonths, BigDecimal price, Boolean immediateUpgrade, SubscriptionRightsResponse rights,
        LocalDateTime expiresAt, LocalDateTime effectiveAt, LocalDateTime endAt,
        LocalDateTime appliedAt, LocalDateTime confirmedAt, LocalDateTime createdAt) {
}
