package com.tamdaptit.rentalmanagement.property.dto.request;

import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CreatePropertyRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String propertyTypeId,
        @NotNull PropertyRentalMode rentalMode,
        @Min(0) Integer totalFloors,
        @NotBlank String provinceId,
        @NotBlank String wardId,
        @NotBlank @Size(max = 255) String addressLine,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Valid DefaultRoomRequest defaultRoom,
        List<@NotBlank String> amenityIds,
        @NotEmpty List<@NotNull @Valid CreateFloorRequest> floors) {}
