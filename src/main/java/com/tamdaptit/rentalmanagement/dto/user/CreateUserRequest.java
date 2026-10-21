package com.tamdaptit.rentalmanagement.dto.user;

import com.tamdaptit.rentalmanagement.entity.enums.RoleCode;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = "^(|(\\+84|84|0)[0-9]{9})$") String phone,
        @Size(max = 20) String citizenId,
        @NotNull RoleCode roleCode) {}
