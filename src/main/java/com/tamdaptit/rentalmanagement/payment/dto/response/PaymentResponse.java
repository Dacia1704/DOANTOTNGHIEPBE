package com.tamdaptit.rentalmanagement.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentType;

public record PaymentResponse(
    String id,
    PaymentType type,
    String targetId,
    String merchantOrderId,
    BigDecimal amount,
    PaymentStatus status,
    LocalDateTime expiresAt,
    LocalDateTime paidAt,
    Boolean requiresReview,
    String reviewReason,
    String invoiceId
) {}
