package com.tamdaptit.rentalmanagement.subscription.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BoostAllocationRequest(@NotBlank String boostSubscriptionId, @NotNull @Min(1) @Max(2) Integer level) {}
