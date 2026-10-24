package com.tamdaptit.rentalmanagement.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceResponse(String id, String invoiceNo, BigDecimal taxAmount,
        BigDecimal totalAmount, String buyerName, LocalDateTime issuedAt) {
}
