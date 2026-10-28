package com.tamdaptit.rentalmanagement.property.dto.request;

import jakarta.validation.constraints.*;

public record PropertyTypeRequest(@NotBlank @Size(max = 100) String name, String description) {}
