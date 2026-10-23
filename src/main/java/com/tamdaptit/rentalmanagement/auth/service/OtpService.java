package com.tamdaptit.rentalmanagement.auth.service;

import com.tamdaptit.rentalmanagement.auth.entity.enums.OtpPurpose;

public interface OtpService {

    /**
     * Tao OTP ngau nhien, luu vao Redis voi TTL, va tra ve gia tri OTP de gui qua email.
     * Neu da co OTP cu, ghi de luon (reset TTL).
     *
     * @param email   email nguoi dung (da normalize)
     * @param purpose muc dich su dung OTP
     * @return gia tri OTP vua tao (6 chu so)
     */
    String generateAndSave(String email, OtpPurpose purpose);

    /**
     * Xac thuc OTP. Nem AppException neu OTP sai hoac het han.
     * Xoa OTP khoi Redis ngay sau khi xac thuc thanh cong.
     *
     * @param email   email nguoi dung
     * @param otp     OTP nguoi dung nhap
     * @param purpose muc dich su dung OTP
     */
    void verify(String email, String otp, OtpPurpose purpose);

    /**
     * Kiem tra cooldown (gioi han gui lai OTP).
     * Nem AppException neu nguoi dung gui OTP qua nhieu lan trong thoi gian ngan.
     *
     * @param email   email nguoi dung
     * @param purpose muc dich su dung OTP
     */
    void checkCooldown(String email, OtpPurpose purpose);
}
