package com.tamdaptit.rentalmanagement.room.dto.response;

import java.math.BigDecimal;

import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;

public record RoomSummaryResponse(
    String id,
    BigDecimal price,
    BigDecimal areaM2,
    Integer maxOccupants,
    String addressLine,
    String provinceName,
    String wardName,
    RoomRentalStatus rentalStatus
) {}
