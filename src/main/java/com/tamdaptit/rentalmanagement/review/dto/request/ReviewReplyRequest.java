package com.tamdaptit.rentalmanagement.review.dto.request;
import jakarta.validation.constraints.*;
public record ReviewReplyRequest(@NotBlank @Size(max=2000) String reply) {}
