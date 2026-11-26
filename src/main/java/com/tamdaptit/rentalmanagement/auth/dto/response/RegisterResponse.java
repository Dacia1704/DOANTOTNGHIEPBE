package com.tamdaptit.rentalmanagement.auth.dto.response;

import java.util.List;

import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;

public record RegisterResponse(
    String id,
    String email,
    String phone,
    String fullName,
    UserStatus status,
    RoleCode roleCode,
    List<String> authorities,
    String accessToken,
    String refreshToken
) {}
