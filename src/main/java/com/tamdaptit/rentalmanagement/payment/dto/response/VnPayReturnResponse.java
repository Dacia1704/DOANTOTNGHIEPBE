package com.tamdaptit.rentalmanagement.payment.dto.response;

public record VnPayReturnResponse(boolean signatureValid, String merchantOrderId, String responseCode,
        String transactionStatus, String message) {
}
