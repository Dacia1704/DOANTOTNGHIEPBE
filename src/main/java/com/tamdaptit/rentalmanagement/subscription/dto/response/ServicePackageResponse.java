package com.tamdaptit.rentalmanagement.subscription.dto.response;

import java.time.LocalDateTime;

public record ServicePackageResponse(
        String id,
        String code,
        String name,
        Integer maxListings,
        Integer highPrioritySlots,
        Integer lowPrioritySlots,
        Integer defaultPriorityLevel,
        Boolean hasStats,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
