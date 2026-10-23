package com.tamdaptit.rentalmanagement.common.service;

import com.tamdaptit.rentalmanagement.auth.entity.enums.OtpPurpose;

public interface EmailService {

    /**
     * Gui email chua ma OTP toi nguoi dung.
     *
     * @param to      dia chi email nguoi nhan
     * @param otp     ma OTP 6 chu so
     * @param purpose muc dich (xac thuc email hoac quen mat khau)
     */
    void sendOtpEmail(String to, String otp, OtpPurpose purpose);
}
