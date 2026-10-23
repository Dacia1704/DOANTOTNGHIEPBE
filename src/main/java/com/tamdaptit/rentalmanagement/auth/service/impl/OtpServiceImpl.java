package com.tamdaptit.rentalmanagement.auth.service.impl;

import com.tamdaptit.rentalmanagement.auth.entity.enums.OtpPurpose;
import com.tamdaptit.rentalmanagement.auth.service.OtpService;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    /**
     * Redis key format:
     *   otp:{PURPOSE}:{email}       → gia tri OTP
     *   otp:cooldown:{PURPOSE}:{email} → marker cooldown (khong co value, chi TTL)
     */
    private static final String OTP_KEY_PREFIX      = "otp:";
    private static final String COOLDOWN_KEY_PREFIX  = "otp:cooldown:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiration-seconds:300}")
    private long expirationSeconds;

    @Value("${app.otp.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${app.otp.length:6}")
    private int otpLength;

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    @Override
    public String generateAndSave(String email, OtpPurpose purpose) {
        String otp = generateOtp();
        String key = otpKey(purpose, email);
        // ghi de OTP cu (neu co); reset TTL
        redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(expirationSeconds));
        // danh dau cooldown
        redisTemplate.opsForValue().set(
                cooldownKey(purpose, email), "1", Duration.ofSeconds(cooldownSeconds));
        return otp;
    }

    @Override
    public void verify(String email, String otp, OtpPurpose purpose) {
        String key = otpKey(purpose, email);
        String stored = redisTemplate.opsForValue().get(key);

        if (stored == null || !stored.equals(otp)) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }
        // Xoa OTP ngay sau khi xac thuc thanh cong (one-time use)
        redisTemplate.delete(key);
    }

    @Override
    public void checkCooldown(String email, OtpPurpose purpose) {
        String key = cooldownKey(purpose, email);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new AppException(ErrorCode.OTP_TOO_MANY_REQUESTS);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String generateOtp() {
        int bound = (int) Math.pow(10, otpLength);
        int value = secureRandom.nextInt(bound);
        // Dam bao du so chu so (pad zero phia truoc neu can)
        return String.format("%0" + otpLength + "d", value);
    }

    private String otpKey(OtpPurpose purpose, String email) {
        return OTP_KEY_PREFIX + purpose.name() + ":" + email;
    }

    private String cooldownKey(OtpPurpose purpose, String email) {
        return COOLDOWN_KEY_PREFIX + purpose.name() + ":" + email;
    }
}
