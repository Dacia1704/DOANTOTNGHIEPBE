package com.tamdaptit.rentalmanagement.rental.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RentalAgreementConfirmRequest(
    @NotNull @AssertTrue Boolean accepted,
    @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedPdfSha256
) {}
