package com.tamdaptit.rentalmanagement.storage.service;

/**
 * Cac thu muc luu tru dung chung cho toan he thong.
 *
 * <p>Khong goi tay duong dan o tung module de tranh sai quy uoc.
 * Moi module lay gia tri cua no bang {@link #forUser}, {@link #forRoom}, ...
 * roi truyen thang cho {@link ImageStorageService}.
 *
 * <p>Chung tao theo mau {@code <ten-module>/<id>}: de xoa theo mot doi tuong
 * thi goi {@link ImageStorageService#deleteByPrefix} mot lan la xong.
 */
public final class StorageFolder {

    private static final String USERS = "avatars";
    private static final String ROOMS = "rooms";
    private static final String REPORTS = "report-evidences";
    private static final String AGREEMENTS = "agreements";

    private StorageFolder() {
    }

    /** Anh dai dien cua nguoi dung. */
    public static String forUser(String userId) {
        return USERS + "/" + requireId(userId);
    }

    /** Anh cua mot phong. */
    public static String forRoom(String roomId) {
        return ROOMS + "/" + requireId(roomId);
    }

    /** Anh bang chung cua mot bao cao. */
    public static String forReport(String reportId) {
        return REPORTS + "/" + requireId(reportId);
    }

    /** File hop dong thue. */
    public static String forAgreement(String agreementId) {
        return AGREEMENTS + "/" + requireId(agreementId);
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Thieu dinh danh doi tuong de tao thu muc luu tru");
        }
        String trimmed = id.trim();
        if (trimmed.contains("/") || trimmed.contains("..")) {
            throw new IllegalArgumentException("Dinh danh doi tuong khong hop le: " + id);
        }
        return trimmed;
    }
}