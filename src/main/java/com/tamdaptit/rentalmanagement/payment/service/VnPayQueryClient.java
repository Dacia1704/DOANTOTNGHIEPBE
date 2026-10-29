package com.tamdaptit.rentalmanagement.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.payment.config.VnPayConfig;
import com.tamdaptit.rentalmanagement.payment.dto.response.PaymentQuerySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnPayQueryClient {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private static final String[] REQUEST_FIELDS = {"vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
            "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo"};
    private static final String[] RESPONSE_FIELDS = {"vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
            "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount"};
    private final VnPayConfig config;
    private final VnPaySigner signer;
    private final ObjectMapper mapper;
    private final Clock subscriptionClock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Map<String, String> query(PaymentQuerySnapshot payment) {
        signer.requireConfigured();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", ""));
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_TmnCode", config.getTerminalId());
        fields.put("vnp_TxnRef", payment.merchantOrderId());
        fields.put("vnp_TransactionDate", payment.transactionDate().format(TIME));
        fields.put("vnp_CreateDate", LocalDateTime.now(subscriptionClock).format(TIME));
        fields.put("vnp_IpAddr", config.getQueryIp());
        fields.put("vnp_OrderInfo", "Kiem tra " + payment.merchantOrderId());
        fields.put("vnp_SecureHash", signer.signData(data(fields, REQUEST_FIELDS)));
        String requestId = fields.get("vnp_RequestId");
        log.debug("VNPay QueryDr request: requestId={}, paymentId={}, merchantOrderId={}, transactionDate={}, createDate={}, queryIp={}",
                requestId, payment.id(), payment.merchantOrderId(), fields.get("vnp_TransactionDate"),
                fields.get("vnp_CreateDate"), config.getQueryIp());
        try {
            var request = HttpRequest.newBuilder(URI.create(config.getQueryUrl())).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(fields), StandardCharsets.UTF_8)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("VNPay QueryDr HTTP error: requestId={}, paymentId={}, httpStatus={}",
                        requestId, payment.id(), response.statusCode());
                throw new AppException(ErrorCode.VNPAY_QUERY_FAILED);
            }
            return verify(response.body(), payment, requestId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("VNPay QueryDr interrupted: requestId={}, paymentId={}", requestId, payment.id());
            throw new AppException(ErrorCode.VNPAY_QUERY_FAILED);
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("VNPay QueryDr request or response error: requestId={}, paymentId={}, failureType={}",
                    requestId, payment.id(), exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.VNPAY_QUERY_FAILED);
        }
    }

    private Map<String, String> verify(String json, PaymentQuerySnapshot payment, String requestId) throws IOException {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new AppException(ErrorCode.VNPAY_QUERY_INVALID);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        var names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = root.get(name);
            if (!value.isNull() && !value.isTextual() && !value.isNumber()) {
                throw new AppException(ErrorCode.VNPAY_QUERY_INVALID);
            }
            fields.put(name, value.isNull() ? "" : value.asText());
        }
        // Some provider error replies have no signature. They can report failure but never change local state.
        String code = fields.get("vnp_ResponseCode");
        if (!"00".equals(code)) {
            log.warn("VNPay QueryDr provider error: requestId={}, paymentId={}, merchantOrderId={}, responseCode={}, providerMessage={}",
                    requestId, payment.id(), payment.merchantOrderId(), logValue(code), logValue(fields.get("vnp_Message")));
            throw new AppException(ErrorCode.VNPAY_QUERY_FAILED,
                    "VNPay chưa xác nhận được giao dịch" + (code != null && code.matches("[0-9]{2}") ? " (mã " + code + ")" : ""));
        }
        if (!signer.verifyData(data(fields, RESPONSE_FIELDS), fields.get("vnp_SecureHash"))
                || !config.getTerminalId().equals(fields.get("vnp_TmnCode"))
                || !payment.merchantOrderId().equals(fields.get("vnp_TxnRef"))
                || !payment.amount().movePointRight(2).toBigIntegerExact().toString().equals(fields.get("vnp_Amount"))
                || (!fields.getOrDefault("vnp_Command", "").isEmpty() && !"querydr".equals(fields.get("vnp_Command")))
                || !"01".equals(fields.get("vnp_TransactionType"))
                || !fields.getOrDefault("vnp_TransactionStatus", "").matches("00|01|02|04|05|06|07|09")) {
            log.warn("VNPay QueryDr response verification failed: requestId={}, paymentId={}, merchantOrderId={}",
                    requestId, payment.id(), payment.merchantOrderId());
            throw new AppException(ErrorCode.VNPAY_QUERY_INVALID);
        }
        return Map.copyOf(fields);
    }

    private String logValue(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace(config.getHashSecret(), "[redacted]")
                .replaceAll("[a-fA-F0-9]{128}", "[redacted]")
                .replaceAll("[\\p{Cntrl}]+", " ");
        return safe.substring(0, Math.min(safe.length(), 256));
    }

    private String data(Map<String, String> fields, String[] names) {
        return java.util.Arrays.stream(names).map(name -> fields.getOrDefault(name, ""))
                .collect(java.util.stream.Collectors.joining("|"));
    }
}
