package com.tamdaptit.rentalmanagement.common.service.impl;

import com.tamdaptit.rentalmanagement.auth.entity.enums.OtpPurpose;
import com.tamdaptit.rentalmanagement.common.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@rental.local}")
    private String fromAddress;

    @Value("${app.otp.expiration-seconds:300}")
    private long expirationSeconds;

    @Override
    @Async
    public void sendOtpEmail(String to, String otp, OtpPurpose purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(buildSubject(purpose));
            helper.setText(buildBody(otp, purpose), false);

            mailSender.send(message);
            log.info("[EmailService] Gui OTP email thanh cong: to={}, purpose={}", to, purpose);

        } catch (Exception e) {
            // Ghi log loi nhung khong throw de tranh anh huong den luong chinh
            log.error("[EmailService] Gui OTP email that bai: to={}, purpose={}, error={}",
                    to, purpose, e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String buildSubject(OtpPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFY   -> "[Rental] Xac thuc dia chi email";
            case FORGOT_PASSWORD -> "[Rental] Dat lai mat khau";
        };
    }

    private String buildBody(String otp, OtpPurpose purpose) {
        long minutesValid = expirationSeconds / 60;
        String action = switch (purpose) {
            case EMAIL_VERIFY    -> "xac thuc dia chi email";
            case FORGOT_PASSWORD -> "dat lai mat khau";
        };

        return String.format("""
                Xin chao,

                Ma OTP de %s cua ban la:

                    %s

                Ma co hieu luc trong %d phut. Vui long khong chia se ma nay voi bat ky ai.

                Neu ban khong yeu cau hanh dong nay, hay bo qua email nay.

                Tran trong,
                He thong Quan ly Cho thue Phong tro
                """, action, otp, minutesValid);
    }
}
