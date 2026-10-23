package com.tamdaptit.rentalmanagement.auth.entity.enums;

/**
 * Phan loai muc dich su dung OTP.
 * Dung de tao Redis key rieng biet cho tung luong,
 * tranh nham lan OTP giua cac tinh huong khac nhau.
 */
public enum OtpPurpose {

    /** Xac thuc email sau khi dang ky. */
    EMAIL_VERIFY,

    /** Dat lai mat khau khi nguoi dung quen. */
    FORGOT_PASSWORD
}
