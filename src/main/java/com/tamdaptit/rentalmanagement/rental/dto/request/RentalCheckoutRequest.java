package com.tamdaptit.rentalmanagement.rental.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RentalCheckoutRequest(@NotNull @AssertTrue Boolean handoverConfirmed, @Size(max = 500) String reason) {}
