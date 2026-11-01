package com.tamdaptit.rentalmanagement.rental.dto.request;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RentalRequestCreateRequest(
    @NotBlank @Size(max=36) String listingId,
    @NotBlank @Size(max=36) String roomId,
    @NotBlank @Size(min=2,max=100) String tenantFullName,
    @NotBlank @Pattern(regexp="\\+?[0-9]{9,15}") @Size(max=20) String tenantPhone,
    @NotBlank @Email @Size(max=255) String tenantEmail,
    @NotBlank @Pattern(regexp="([0-9]{9}|[0-9]{12})") String tenantCitizenId,
    @NotBlank @Size(min=5,max=500) String tenantPermanentAddress,
    @NotNull LocalDate proposedMoveInDate,
    @NotNull @Min(1) @Max(36) @tools.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class) Integer durationMonths,
    @NotNull @Min(1) @tools.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class) Integer occupantCount,
    @Size(max=2000) String message,
    @NotNull @AssertTrue Boolean acceptedPrivacyNotice) {}
