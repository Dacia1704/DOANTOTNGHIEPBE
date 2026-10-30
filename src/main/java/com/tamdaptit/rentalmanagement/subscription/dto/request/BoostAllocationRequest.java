package com.tamdaptit.rentalmanagement.subscription.dto.request;

import jakarta.validation.constraints.*;

public record BoostAllocationRequest(@NotBlank String boostSubscriptionId, @NotNull @Min(1) @Max(2) Integer level) {
}
