package com.tamdaptit.rentalmanagement.auth.dto.response;

import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import java.util.List;

public record RegisterResponse(
        String id,
        String email,
        String phone,
        String fullName,
        UserStatus status,
        RoleCode roleCode,
        List<String> authorities,
        String accessToken,
        String refreshToken) {
}
