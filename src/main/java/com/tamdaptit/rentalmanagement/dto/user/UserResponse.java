package com.tamdaptit.rentalmanagement.dto.user;

import com.tamdaptit.rentalmanagement.entity.User;
import com.tamdaptit.rentalmanagement.entity.enums.*;
import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(String id, String email, String phone, String fullName,
                           String avatarUrl, String citizenId, UserStatus status,
                           LocalDateTime emailVerifiedAt, LocalDateTime phoneVerifiedAt,
                           LocalDateTime createdAt, LocalDateTime updatedAt, List<RoleCode> roles) {
    public static UserResponse from(User user) {
        var roles = user.getUserRoles().stream().map(ur -> ur.getRole().getCode()).distinct().sorted().toList();
        return new UserResponse(user.getId(), user.getEmail(), user.getPhone(), user.getFullName(),
                user.getAvatarUrl(), user.getCitizenId(), user.getStatus(), user.getEmailVerifiedAt(),
                user.getPhoneVerifiedAt(), user.getCreatedAt(), user.getUpdatedAt(), roles);
    }
}
