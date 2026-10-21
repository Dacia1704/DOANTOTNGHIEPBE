package com.tamdaptit.rentalmanagement.dto.user;

import jakarta.validation.constraints.*;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 100) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        @NotBlank @Size(max = 72) String confirmPassword) {}
