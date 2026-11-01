package com.tamdaptit.rentalmanagement.rental.dto.request;
import jakarta.validation.constraints.*;
public record RentalCheckoutRequest(@NotNull @AssertTrue Boolean handoverConfirmed,@Size(max=500) String reason) {}
