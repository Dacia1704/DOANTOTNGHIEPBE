package com.tamdaptit.rentalmanagement.address.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WardRequest(
        @NotBlank @Pattern(regexp = "[0-9]{5}", message = "Ward code must contain 5 digits") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 36) String provinceId) {
}
