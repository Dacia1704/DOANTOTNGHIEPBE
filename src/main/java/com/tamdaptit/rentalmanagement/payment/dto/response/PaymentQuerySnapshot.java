package com.tamdaptit.rentalmanagement.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;

public record PaymentQuerySnapshot(String id, String merchantOrderId, BigDecimal amount,
        LocalDateTime transactionDate, PaymentStatus status) {
}
