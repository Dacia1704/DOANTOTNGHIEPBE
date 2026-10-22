package com.tamdaptit.rentalmanagement.auth.mapper;

import com.tamdaptit.rentalmanagement.auth.dto.response.LoginResponse;
import com.tamdaptit.rentalmanagement.auth.dto.response.RefreshTokenResponse;
import com.tamdaptit.rentalmanagement.auth.dto.response.RegisterResponse;
import com.tamdaptit.rentalmanagement.identity.entity.Role;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.UserRole;
import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import org.mapstruct.Mapper;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Chuyen doi response cho module auth.
 *
 * <p>Cac response co tham so tinh toan (token, danh sach quyen) khong map duoc
 * tu {@link User} bang {@code @Mapping} thuan, nen giu lai {@code default} method
 * de ghep thu cong.
 */
@Mapper(componentModel = "spring")
public interface AuthMapper {

    default List<RoleCode> mapRoleCodes(User user) {
        if (user == null || user.getUserRoles() == null) {
            return List.of();
        }
        return user.getUserRoles().stream()
                .filter(Objects::nonNull)
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .map(Role::getCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    default RoleCode mapPrimaryRoleCode(User user) {
        if (user == null || user.getUserRoles() == null) {
            return null;
        }
        return user.getUserRoles().stream()
                .filter(Objects::nonNull)
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .map(Role::getCode)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    default LoginResponse toLoginResponse(
            User user,
            List<String> authorities,
            String accessToken,
            String refreshToken,
            LocalDateTime accessTokenExpiresAt) {
        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getStatus(),
                mapRoleCodes(user),
                authorities,
                accessToken,
                refreshToken,
                accessTokenExpiresAt);
    }

    default RegisterResponse toRegisterResponse(
            User user,
            List<String> authorities,
            String accessToken,
            String refreshToken) {
        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getStatus(),
                mapPrimaryRoleCode(user),
                authorities,
                accessToken,
                refreshToken);
    }

    default RefreshTokenResponse toRefreshTokenResponse(
            String accessToken,
            String refreshToken,
            LocalDateTime accessTokenExpiresAt) {
        return new RefreshTokenResponse(accessToken, refreshToken, accessTokenExpiresAt);
    }
}
