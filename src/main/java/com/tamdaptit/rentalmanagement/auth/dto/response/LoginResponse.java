package com.tamdaptit.rentalmanagement.auth.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;

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
    LocalDateTime accessTokenExpiresAt
) {}
