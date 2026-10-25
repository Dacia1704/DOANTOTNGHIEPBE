package com.tamdaptit.rentalmanagement.payment.service;

import com.tamdaptit.rentalmanagement.payment.dto.response.VnPayQueryResponse;

public interface PaymentQueryService {
    VnPayQueryResponse check(String userId, String paymentId, boolean admin);
}
