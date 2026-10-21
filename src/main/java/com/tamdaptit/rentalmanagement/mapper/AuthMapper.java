package com.tamdaptit.rentalmanagement.mapper;

import com.tamdaptit.rentalmanagement.dto.auth.response.LoginResponse;
import com.tamdaptit.rentalmanagement.dto.auth.response.RefreshTokenResponse;
import com.tamdaptit.rentalmanagement.dto.auth.response.RegisterResponse;
import com.tamdaptit.rentalmanagement.entity.User;
import com.tamdaptit.rentalmanagement.entity.UserRole;
import com.tamdaptit.rentalmanagement.entity.enums.RoleCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    default List<RoleCode> mapRoleCodes(User user) {
        if (user == null || user.getUserRoles() == null) {
            return List.of();
        }
        return user.getUserRoles()
                .stream()
                .filter(Objects::nonNull)
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .map(r -> r.getCode())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    default RoleCode mapPrimaryRoleCode(User user) {
        if (user == null || user.getUserRoles() == null) {
            return null;
        }
        return user.getUserRoles()
                .stream()
                .filter(Objects::nonNull)
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .map(r -> r.getCode())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    default LoginResponse toLoginResponse(
            User user,
            List<String> authorities,
            String accessToken,
            String refreshToken,
            LocalDateTime accessTokenExpiresAt
    ) {
        return LoginResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(mapRoleCodes(user))
                .authorities(authorities)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresAt(accessTokenExpiresAt)
                .build();
    }

    default RegisterResponse toRegisterResponse(
            User user,
            List<String> authorities,
            String accessToken,
            String refreshToken
    ) {
        return RegisterResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .roleCode(mapPrimaryRoleCode(user))
                .authorities(authorities)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    default RefreshTokenResponse toRefreshTokenResponse(
            String accessToken,
            String refreshToken,
            LocalDateTime accessTokenExpiresAt
    ) {
        return RefreshTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresAt(accessTokenExpiresAt)
                .build();
    }
}
