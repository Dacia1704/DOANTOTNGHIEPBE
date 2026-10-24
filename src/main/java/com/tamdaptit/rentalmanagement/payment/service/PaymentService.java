package com.tamdaptit.rentalmanagement.payment.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.payment.dto.request.PaymentRequest;
import com.tamdaptit.rentalmanagement.payment.dto.response.*;
import org.springframework.util.MultiValueMap;

public interface PaymentService {
    PaymentCheckoutResponse checkout(String landlordId, PaymentRequest request, String ipAddress);
    PaymentResponse getById(String userId, String id, boolean admin);
    InvoiceResponse getInvoice(String userId, String paymentId, boolean admin);
    PageResponse<PaymentResponse> getHistory(String userId, int page, int size);
    PageResponse<PaymentResponse> getReviewQueue(int page, int size);
    VnPayIpnResponse processIpn(MultiValueMap<String, String> parameters);
    VnPayReturnResponse verifyReturn(MultiValueMap<String, String> parameters);
    PaymentQuerySnapshot getQuerySnapshot(String userId, String id, boolean admin);
    PaymentResponse applyQueryResult(String userId, String id, boolean admin, java.util.Map<String, String> verified);
}
