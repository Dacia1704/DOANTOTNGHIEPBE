package com.tamdaptit.rentalmanagement.subscription;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.payment.config.VnPayConfig;
import com.tamdaptit.rentalmanagement.payment.dto.response.PaymentQuerySnapshot;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.payment.service.VnPayQueryClient;
import com.tamdaptit.rentalmanagement.payment.service.VnPaySigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VnPayQueryClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> requestBody = new AtomicReference<>();
    private final AtomicInteger httpStatus = new AtomicInteger(200);
    private final PaymentQuerySnapshot payment = new PaymentQuerySnapshot("payment", "reference", new BigDecimal("100"),
            LocalDateTime.parse("2026-01-31T10:00:00"), PaymentStatus.EXPIRED);
    private HttpServer server;
    private VnPayQueryClient client;

    @BeforeEach
    void startProviderStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/transaction", exchange -> {
            requestBody.set(mapper.readValue(exchange.getRequestBody(), new TypeReference<>() {}));
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus.get(), bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        var config = new VnPayConfig("TESTCODE", "test-only-signature-key", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "http://localhost/return", "http://127.0.0.1:" + server.getAddress().getPort() + "/transaction", "127.0.0.1");
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var clock = Clock.fixed(LocalDateTime.parse("2026-02-01T12:00:00").atZone(zone).toInstant(), zone);
        client = new VnPayQueryClient(config, new VnPaySigner(config), mapper, clock);
        respond(baseResponse(), true);
    }

    @AfterEach
    void stopProviderStub() { if (server != null) server.stop(0); }

    private Map<String, String> baseResponse() {
        var fields = new HashMap<String, String>();
        fields.put("vnp_ResponseId", "response1"); fields.put("vnp_Command", "querydr");
        fields.put("vnp_ResponseCode", "00"); fields.put("vnp_Message", "Success");
        fields.put("vnp_TmnCode", "TESTCODE"); fields.put("vnp_TxnRef", "reference");
        fields.put("vnp_Amount", "10000"); fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20260131100500"); fields.put("vnp_TransactionNo", "12345");
        fields.put("vnp_TransactionType", "01"); fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_OrderInfo", "Thanh toan reference");
        fields.put("vnp_PromotionCode", ""); fields.put("vnp_PromotionAmount", "");
        return fields;
    }

    private String hmac(String data) throws Exception {
        var mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("test-only-signature-key".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private void respond(Map<String, String> fields, boolean validSignature) throws Exception {
        String checksum = hmac(String.join("|", fields.getOrDefault("vnp_ResponseId", ""), fields.getOrDefault("vnp_Command", ""),
                fields.getOrDefault("vnp_ResponseCode", ""), fields.getOrDefault("vnp_Message", ""), fields.getOrDefault("vnp_TmnCode", ""),
                fields.getOrDefault("vnp_TxnRef", ""), fields.getOrDefault("vnp_Amount", ""), fields.getOrDefault("vnp_BankCode", ""),
                fields.getOrDefault("vnp_PayDate", ""), fields.getOrDefault("vnp_TransactionNo", ""), fields.getOrDefault("vnp_TransactionType", ""),
                fields.getOrDefault("vnp_TransactionStatus", ""), fields.getOrDefault("vnp_OrderInfo", ""),
                fields.getOrDefault("vnp_PromotionCode", ""), fields.getOrDefault("vnp_PromotionAmount", "")));
        fields.put("vnp_SecureHash", validSignature ? checksum : "0".repeat(128));
        responseBody.set(mapper.writeValueAsString(fields));
    }

    @Test
    void sendsSignedJsonUsingOriginalCheckoutDateAndVerifiesProviderResponse() throws Exception {
        var response = client.query(payment);
        assertEquals("00", response.get("vnp_TransactionStatus"));
        var request = requestBody.get();
        assertEquals("querydr", request.get("vnp_Command"));
        assertEquals("20260131100000", request.get("vnp_TransactionDate"));
        assertEquals("20260201120000", request.get("vnp_CreateDate"));
        assertEquals(32, request.get("vnp_RequestId").length());
        assertEquals(hmac(request.get("vnp_RequestId") + "|2.1.0|querydr|TESTCODE|reference|20260131100000|20260201120000|127.0.0.1|Kiem tra reference"),
                request.get("vnp_SecureHash"));
    }

    @Test
    void rejectsInvalidSignatureAndSignedResponsesForAnotherTargetAmountOrTransactionType() throws Exception {
        respond(baseResponse(), false);
        assertThrows(AppException.class, () -> client.query(payment));
        for (var override : Map.of("vnp_TmnCode", "OTHER", "vnp_TxnRef", "another-reference", "vnp_Amount", "99900",
                "vnp_TransactionType", "02", "vnp_Command", "refund", "vnp_TransactionStatus", "99").entrySet()) {
            var fields = baseResponse(); fields.put(override.getKey(), override.getValue()); respond(fields, true);
            assertThrows(AppException.class, () -> client.query(payment), override.getKey());
        }
    }

    @Test
    void providerNotFoundMalformedJsonAndHttpErrorsAreNotTreatedAsPaymentSuccess() {
        responseBody.set("{\"vnp_ResponseCode\":\"91\",\"vnp_Message\":\"Not found\"}");
        assertTrue(assertThrows(AppException.class, () -> client.query(payment)).getMessage().contains("91"));
        responseBody.set("not-json");
        assertThrows(AppException.class, () -> client.query(payment));
        httpStatus.set(503);
        assertThrows(AppException.class, () -> client.query(payment));
    }

    @Test
    void successfulQueryDoesNotMeanSuccessfulTransactionWhenProviderStillReportsPending() throws Exception {
        var fields = baseResponse(); fields.put("vnp_TransactionStatus", "01"); respond(fields, true);
        assertEquals("01", client.query(payment).get("vnp_TransactionStatus"));
    }
}
