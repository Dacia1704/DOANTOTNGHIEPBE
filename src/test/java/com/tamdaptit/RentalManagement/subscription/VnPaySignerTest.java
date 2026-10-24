package com.tamdaptit.rentalmanagement.subscription;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.payment.config.VnPayConfig;
import com.tamdaptit.rentalmanagement.payment.service.VnPaySigner;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VnPaySignerTest {
    private final VnPaySigner signer = new VnPaySigner(new VnPayConfig("TESTCODE", "test-only-signature-key",
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "http://localhost/return", "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction", "127.0.0.1"));

    @Test
    void sortingEncodingAndHmacMatchAnIndependentlyComputedVector() {
        var fields = Map.of("vnp_TxnRef", "abc", "vnp_Amount", "10000", "vnp_OrderInfo", "Thanh toan + &");
        assertEquals("vnp_Amount=10000&vnp_OrderInfo=Thanh+toan+%2B+%26&vnp_TxnRef=abc", signer.canonical(fields));
        assertEquals("86961c6cb90a4f74026c8fa39e00d69f50df51a00ceb1ca328e6d0a5b607ec95f698ff0775f332792d408a1a8129ecf6ef531427e17224e09a0915d6c1cedd62",
                signer.sign(fields));
        var callback = new LinkedMultiValueMap<String, String>();
        fields.forEach(callback::set);
        callback.set("vnp_SecureHash", signer.sign(fields).toUpperCase());
        assertTrue(signer.verify(callback));
        callback.set("vnp_OrderInfo", "changed");
        assertFalse(signer.verify(callback));
    }

    @Test
    void missingConfigurationAndMalformedHashesCannotBeUsed() {
        var absent = new VnPaySigner(new VnPayConfig("TESTCODE", "", "https://sandbox.vnpayment.vn", "http://localhost/return",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction", "127.0.0.1"));
        assertThrows(AppException.class, () -> absent.sign(Map.of("vnp_TxnRef", "abc")));
        var fields = new LinkedMultiValueMap<String, String>();
        assertFalse(signer.verify(fields));
        fields.set("vnp_SecureHash", "incorrect");
        assertFalse(signer.verify(fields));
    }
}
