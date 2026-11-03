package com.tamdaptit.rentalmanagement.review.dto.request;
import jakarta.validation.constraints.*;
public record ReviewVisibilityRequest(@NotNull Boolean hidden, @Size(max=500) String reason) {}
