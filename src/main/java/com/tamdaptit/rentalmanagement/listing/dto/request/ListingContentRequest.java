package com.tamdaptit.rentalmanagement.listing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ListingContentRequest(@NotBlank @Size(max = 255) String title,
        @Size(max = 20000) String description) {
}
