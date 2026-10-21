package com.tamdaptit.rentalmanagement.dto.user;

import jakarta.validation.constraints.*;

// null leaves a field unchanged; empty phone/citizenId clears that field.
public record UpdateProfileRequest(
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") String fullName,
        @Pattern(regexp = "^(|(\\+84|84|0)[0-9]{9})$") String phone,
        @Size(max = 20) String citizenId) {}
