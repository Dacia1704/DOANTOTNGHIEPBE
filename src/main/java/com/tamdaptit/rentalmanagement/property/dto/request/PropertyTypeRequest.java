package com.tamdaptit.rentalmanagement.property.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PropertyTypeRequest(@NotBlank @Size(max = 100) String name, String description) {}
