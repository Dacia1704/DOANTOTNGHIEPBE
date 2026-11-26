package com.tamdaptit.rentalmanagement.review.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewVisibilityRequest(@NotNull Boolean hidden, @Size(max = 500) String reason) {}
