package com.tamdaptit.rentalmanagement.payment.dto.response;

import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(String id, PaymentType type, String targetId, String merchantOrderId,
        BigDecimal amount, PaymentStatus status, LocalDateTime expiresAt, LocalDateTime paidAt,
        Boolean requiresReview, String reviewReason, String invoiceId) {
}
