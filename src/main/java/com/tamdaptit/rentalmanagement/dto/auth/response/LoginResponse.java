package com.tamdaptit.rentalmanagement.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tamdaptit.rentalmanagement.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.entity.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginResponse {

    String id;

    String email;

    String phone;

    String fullName;

    String avatarUrl;

    UserStatus status;

    /** Danh sach ma vai tro: TENANT / LANDLORD / ADMIN */
    List<RoleCode> roles;

    /** Danh sach authorities day du trong token: ROLE_TENANT, LISTING_READ, ... */
    List<String> authorities;

    String accessToken;

    String refreshToken;

    LocalDateTime accessTokenExpiresAt;
}