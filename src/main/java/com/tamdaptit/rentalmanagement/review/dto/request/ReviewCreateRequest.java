package com.tamdaptit.rentalmanagement.review.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.tamdaptit.rentalmanagement.rental.dto.request.StrictIntegerDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewCreateRequest(
    @NotBlank @Size(max = 36) String rentalAgreementId,
    @NotNull @Min(1) @Max(5) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer rating,
    @Size(max = 2000) String comment
) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Trường không được hỗ trợ: " + name);
    }
}
