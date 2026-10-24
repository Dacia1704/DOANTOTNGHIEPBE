package com.tamdaptit.rentalmanagement.listing.dto.response;

import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import java.time.LocalDateTime;

public record ListingResponse(String id, String roomId, String landlordId, String title, String slug,
        String description, ListingStatus status, String rejectionReason, Integer priorityLevel,
        Integer packagePriorityLevel, LocalDateTime submittedAt, LocalDateTime pendingExpiresAt,
        LocalDateTime publishedAt, LocalDateTime expiresAt, LocalDateTime createdAt) {
}
