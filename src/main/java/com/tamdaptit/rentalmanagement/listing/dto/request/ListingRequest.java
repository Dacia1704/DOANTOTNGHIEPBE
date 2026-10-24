package com.tamdaptit.rentalmanagement.listing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ListingRequest(@NotBlank String roomId, @NotBlank @Size(max = 255) String title,
        @Size(max = 20000) String description) {
}
