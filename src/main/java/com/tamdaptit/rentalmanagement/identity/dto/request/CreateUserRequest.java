package com.tamdaptit.rentalmanagement.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = "^(|(\\+84|84|0)[0-9]{9})$") String phone,
        @Size(max = 20) String citizenId,
        @NotNull RoleCode roleCode) {}
