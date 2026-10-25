package com.tamdaptit.rentalmanagement.subscription.dto.response;

import com.tamdaptit.rentalmanagement.subscription.entity.enums.BoostSubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BoostPurchaseResponse(String id, String boostPackageId,
        String packageNameSnapshot, Integer highPrioritySlotsSnapshot, Integer lowPrioritySlotsSnapshot, Integer durationMonthsSnapshot,
        BigDecimal price, BoostSubscriptionStatus status, LocalDateTime startAt, LocalDateTime endAt,
        LocalDateTime paymentExpiresAt, LocalDateTime createdAt) {
}
