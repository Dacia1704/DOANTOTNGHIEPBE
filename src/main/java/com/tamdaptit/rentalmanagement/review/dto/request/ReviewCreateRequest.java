package com.tamdaptit.rentalmanagement.review.dto.request;

import jakarta.validation.constraints.*;
import com.tamdaptit.rentalmanagement.rental.dto.request.StrictIntegerDeserializer;

public record ReviewCreateRequest(
    @NotBlank @Size(max=36) String rentalAgreementId,
    @NotNull @Min(1) @Max(5)
    @tools.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class) Integer rating,
    @Size(max=2000) String comment) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Trường không được hỗ trợ: " + name);
    }
}
