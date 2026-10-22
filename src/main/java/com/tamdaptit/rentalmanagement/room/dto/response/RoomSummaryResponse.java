package com.tamdaptit.rentalmanagement.room.dto.response;

import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import java.math.BigDecimal;

public record RoomSummaryResponse(
        String id,
        BigDecimal price,
        BigDecimal areaM2,
        Integer maxOccupants,
        String addressLine,
        String provinceName,
        String wardName,
        RoomRentalStatus rentalStatus) {
}
