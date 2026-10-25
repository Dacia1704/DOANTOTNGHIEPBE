package com.tamdaptit.rentalmanagement.subscription.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BoostServicePackageResponse(
        String id,
        String code,
        String name,
        Integer highPrioritySlots,
        Integer lowPrioritySlots,
        Integer durationMonths,
        BigDecimal price,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
