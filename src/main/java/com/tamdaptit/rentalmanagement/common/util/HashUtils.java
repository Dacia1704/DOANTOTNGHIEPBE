package com.tamdaptit.rentalmanagement.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Bam chuoi thanh hex SHA-256.
 *
 * <p>Dung cho refresh token: database chi luu ban bam, khong luu token goc,
 * nham khi bi ro DB thi attacker cung khong goi duoc API xoay token.
 *
 * <p>SHA-256 la ham bat bien, khong can secret, nen khong dung BCrypt/argon2
 * o day. Ket qua luon co 64 ky tu hex, nen cot {@code token} chi can VARCHAR(64).
 */
public final class HashUtils {

    private static final HexFormat HEX = HexFormat.of();

    private HashUtils() {
    }

    /**
     * @return 64 ky tu hex cua SHA-256, hoac {@code null} neu input null/blank
     */
    public static String sha256Hex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 la bat buoc theo Java spec, nen truong hop nay khong xay ra.
            throw new IllegalStateException("Khong tim thay thuat toan SHA-256", ex);
        }
    }
}