package com.tamdaptit.rentalmanagement.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;

public record UpdateUserRequest(
        @Email @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*") String email,
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") String fullName,
        @Pattern(regexp = "^(|(\\+84|84|0)[0-9]{9})$") String phone,
        @Size(max = 20) String citizenId,
        UserStatus status) {}
