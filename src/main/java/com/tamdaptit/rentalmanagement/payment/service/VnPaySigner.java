package com.tamdaptit.rentalmanagement.payment.service;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.payment.config.VnPayConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VnPaySigner {
    private final VnPayConfig config;

    public void requireConfigured() {
        if (config.getTerminalId().isBlank() || config.getHashSecret().isBlank()
                || config.getReturnUrl().isBlank()) {
            throw new AppException(ErrorCode.VNPAY_NOT_CONFIGURED);
        }
    }

    public String canonical(Map<String, String> parameters) {
        return new TreeMap<>(parameters).entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_") && !e.getKey().equals("vnp_SecureHash")
                        && !e.getKey().equals("vnp_SecureHashType") && e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    public String sign(Map<String, String> parameters) {
        return signData(canonical(parameters));
    }

    public String signData(String data) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(config.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA512 unavailable", exception);
        }
    }

    public boolean verify(MultiValueMap<String, String> parameters) {
        requireConfigured();
        if (parameters.entrySet().stream().anyMatch(e -> e.getKey().startsWith("vnp_") && e.getValue().size() != 1)) {
            return false;
        }
        return verifyData(canonical(parameters.toSingleValueMap()), parameters.getFirst("vnp_SecureHash"));
    }

    public boolean verifyData(String data, String signature) {
        requireConfigured();
        if (signature == null || !signature.matches("[a-fA-F0-9]{128}")) {
            return false;
        }
        return MessageDigest.isEqual(HexFormat.of().parseHex(signature),
                HexFormat.of().parseHex(signData(data)));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
