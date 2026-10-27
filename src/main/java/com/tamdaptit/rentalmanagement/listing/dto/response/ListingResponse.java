package com.tamdaptit.rentalmanagement.listing.dto.response;

import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import java.time.LocalDateTime;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyResponse;
import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;

public record ListingResponse(String id, String propertyId, String landlordId, String title, String slug,
        String description, ListingStatus status, String rejectionReason, Integer priorityLevel,
        Integer packagePriorityLevel, LocalDateTime submittedAt, LocalDateTime pendingExpiresAt,
        LocalDateTime publishedAt, LocalDateTime expiresAt, LocalDateTime createdAt,
        PropertyResponse property, String propertyName, PropertyRentalMode rentalMode, String landlordName) {
}
