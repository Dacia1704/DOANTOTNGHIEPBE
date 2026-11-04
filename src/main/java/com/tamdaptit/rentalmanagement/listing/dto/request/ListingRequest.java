package com.tamdaptit.rentalmanagement.listing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record ListingRequest(@NotBlank String propertyId, @NotBlank @Size(max = 255) String title,
        @Size(max = 20000) String description, @Min(0) @Max(2) Integer priorityLevel,
        @Size(min = 1, max = 36) String boostSubscriptionId, @Min(1) @Max(2) Integer boostPriorityLevel) {
    public ListingRequest(String propertyId, String title, String description, Integer priorityLevel) {
        this(propertyId, title, description, priorityLevel, null, null);
    }

    public ListingRequest(String propertyId, String title, String description) {
        this(propertyId, title, description, 0);
    }
}
