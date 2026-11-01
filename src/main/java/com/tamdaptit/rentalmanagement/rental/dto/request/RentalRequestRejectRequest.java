package com.tamdaptit.rentalmanagement.rental.dto.request;
import jakarta.validation.constraints.Size;
public record RentalRequestRejectRequest(@Size(max=500) String reason) {}
