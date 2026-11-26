package com.tamdaptit.rentalmanagement.review.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReplyRequest(@NotBlank @Size(max = 2000) String reply) {}
