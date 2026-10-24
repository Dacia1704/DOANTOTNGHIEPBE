package com.tamdaptit.rentalmanagement.subscription.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServicePackagePriceResponse(
        String id,
        String packageId,
        Integer durationMonths,
        BigDecimal price,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
