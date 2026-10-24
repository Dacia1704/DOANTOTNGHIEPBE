package com.tamdaptit.rentalmanagement.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BoostPurchaseRequest(@NotBlank String listingId, @NotBlank String boostPackageId) {
}
