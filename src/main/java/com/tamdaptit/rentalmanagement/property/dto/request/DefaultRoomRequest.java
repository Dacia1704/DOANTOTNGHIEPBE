package com.tamdaptit.rentalmanagement.property.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record DefaultRoomRequest(
        String roomTypeId,
        @DecimalMin(value = "0", inclusive = false) BigDecimal price,
        @DecimalMin("0") BigDecimal electricityPrice,
        @DecimalMin("0") BigDecimal waterPrice,
        @DecimalMin("0") BigDecimal serviceFee,
        @DecimalMin("0") BigDecimal defaultDeposit,
        @DecimalMin(value = "0", inclusive = false) BigDecimal areaM2,
        @Min(1) Integer maxOccupants) {}
