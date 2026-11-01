package com.tamdaptit.rentalmanagement.rental.dto.request;
import jakarta.validation.constraints.*;
public record RentalAgreementConfirmRequest(@NotNull @AssertTrue Boolean accepted,
    @NotBlank @Pattern(regexp="[a-f0-9]{64}") String expectedPdfSha256) {}
