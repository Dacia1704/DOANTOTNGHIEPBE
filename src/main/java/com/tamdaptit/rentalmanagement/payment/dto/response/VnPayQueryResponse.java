package com.tamdaptit.rentalmanagement.payment.dto.response;

public record VnPayQueryResponse(PaymentResponse payment, String providerResponseCode,
        String providerTransactionStatus, String message) {
}
