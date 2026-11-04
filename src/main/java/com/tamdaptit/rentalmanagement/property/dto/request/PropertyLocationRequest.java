package com.tamdaptit.rentalmanagement.property.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PropertyLocationRequest(
        @NotBlank String provinceId,
        @NotBlank String wardId,
        @NotBlank @Size(max = 255) String addressLine,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
