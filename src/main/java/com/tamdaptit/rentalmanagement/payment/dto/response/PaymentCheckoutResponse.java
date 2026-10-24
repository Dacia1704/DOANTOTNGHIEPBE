package com.tamdaptit.rentalmanagement.payment.dto.response;

public record PaymentCheckoutResponse(PaymentResponse payment, String paymentUrl) {
}
