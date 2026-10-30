package com.tamdaptit.rentalmanagement.room.dto.request;

import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RoomRequest(
        @NotBlank String roomTypeId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        @DecimalMin("0.0") BigDecimal electricityPrice,
        @DecimalMin("0.0") BigDecimal waterPrice,
        @DecimalMin("0.0") BigDecimal serviceFee,
        @DecimalMin("0.0") BigDecimal defaultDeposit,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal areaM2,
        @NotNull @DecimalMin("1") Integer maxOccupants,
        @NotBlank String propertyId,
        @NotBlank @Size(max = 30) String roomNumber,
        @jakarta.validation.constraints.Min(0) Integer floorNumber,
        RoomRentalStatus rentalStatus,
        List<String> amenityIds) {
}
