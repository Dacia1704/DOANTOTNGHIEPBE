package com.tamdaptit.rentalmanagement.auth.dto.response;

import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

public record LoginResponse(
        String id,
        String email,
        String phone,
        String fullName,
        String avatarUrl,
        UserStatus status,
        List<RoleCode> roles,
        List<String> authorities,
        String accessToken,
        String refreshToken,
        LocalDateTime accessTokenExpiresAt) {
}
