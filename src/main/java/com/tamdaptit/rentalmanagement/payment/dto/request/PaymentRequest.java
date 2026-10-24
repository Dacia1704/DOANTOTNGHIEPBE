package com.tamdaptit.rentalmanagement.payment.dto.request;

import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(@NotNull PaymentType type, @NotBlank String targetId) {
}
