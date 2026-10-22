package com.tamdaptit.rentalmanagement.auth.dto.response;

import java.time.LocalDateTime;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        LocalDateTime accessTokenExpiresAt) {
}
