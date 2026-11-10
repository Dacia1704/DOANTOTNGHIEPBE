package com.tamdaptit.rentalmanagement.identity.dto.request;

import com.tamdaptit.rentalmanagement.identity.entity.enums.TenantType;
import com.tamdaptit.rentalmanagement.identity.entity.enums.TopPriority;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Full replacement of the authenticated user's rental preferences. */
public record TenantPreferences(
        TenantType tenantType,
        @Min(1) @Max(50) Integer occupants,
        LocalDate moveInDate,
        @Size(max = 36) String desiredProvinceId,
        @Size(max = 5) List<@NotBlank @Size(max = 36) String> wardIds,
        @Size(max = 36) String desiredRoomTypeId,
        @DecimalMin("0") @DecimalMax("9999999999.99") BigDecimal priceMin,
        @DecimalMin("0") @DecimalMax("9999999999.99") BigDecimal priceMax,
        @DecimalMin("0") @DecimalMax("999999.99") BigDecimal areaMin,
        @DecimalMin("0") @DecimalMax("999999.99") BigDecimal areaMax,
        @Size(max = 36) String anchorPlaceId,
        @Size(max = 255) String anchorLabel,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal anchorLatitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal anchorLongitude,
        @DecimalMin("0.1") @DecimalMax("100") BigDecimal maxDistanceKm,
        @Min(0) @Max(50) Integer priceTolerancePct,
        TopPriority topPriority,
        @Size(max = 500) String note,
        boolean allowPersonalization,
        @Size(max = 100) List<@NotBlank @Size(max = 36) String> amenityIds,
        @Size(max = 100) List<@NotBlank @Size(max = 36) String> requiredAmenityIds) {}
