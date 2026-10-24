package com.tamdaptit.rentalmanagement.listing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ListingRejectionRequest(@NotBlank @Size(max = 2000) String reason) {
}
