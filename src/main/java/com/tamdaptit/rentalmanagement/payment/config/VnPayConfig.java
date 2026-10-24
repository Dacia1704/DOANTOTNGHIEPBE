package com.tamdaptit.rentalmanagement.payment.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class VnPayConfig {
    private final String terminalId;
    private final String hashSecret;
    private final String paymentUrl;
    private final String returnUrl;
    private final String queryUrl;
    private final String queryIp;

    public VnPayConfig(@Value("${VNPAY_TMN_CODE:NQC9OREC}") String terminalId,
            @Value("${VNPAY_HASH_SECRET:}") String hashSecret,
            @Value("${VNPAY_PAYMENT_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String paymentUrl,
            @Value("${VNPAY_RETURN_URL:http://localhost:8080/api/payments/vnpay/return}") String returnUrl,
            @Value("${VNPAY_QUERY_URL:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String queryUrl,
            @Value("${VNPAY_QUERY_IP:127.0.0.1}") String queryIp) {
        this.terminalId = terminalId;
        this.hashSecret = hashSecret;
        this.paymentUrl = paymentUrl;
        this.returnUrl = returnUrl;
        this.queryUrl = queryUrl;
        this.queryIp = queryIp;
    }
}
