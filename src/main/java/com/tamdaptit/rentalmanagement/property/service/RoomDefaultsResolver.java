package com.tamdaptit.rentalmanagement.property.service;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.property.dto.request.*;
import java.math.BigDecimal;

public final class RoomDefaultsResolver {
    private RoomDefaultsResolver() {}

    public static DefaultRoomRequest resolve(DefaultRoomRequest defaults, CreateRoomItemRequest room) {
        var d = defaults == null ? new DefaultRoomRequest(null, null, null, null, null, null, null, null) : defaults;
        var merged = new DefaultRoomRequest(value(room.roomTypeId(), d.roomTypeId()),
                value(room.price(), d.price()), value(room.electricityPrice(), d.electricityPrice()),
                value(room.waterPrice(), d.waterPrice()), value(room.serviceFee(), d.serviceFee()),
                value(room.defaultDeposit(), d.defaultDeposit()), value(room.areaM2(), d.areaM2()),
                value(room.maxOccupants(), d.maxOccupants()));
        if (merged.roomTypeId() == null || merged.roomTypeId().isBlank()
                || !positive(merged.price()) || !positive(merged.areaM2())
                || merged.maxOccupants() == null || merged.maxOccupants() <= 0
                || negative(merged.electricityPrice()) || negative(merged.waterPrice())
                || negative(merged.serviceFee()) || negative(merged.defaultDeposit()))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Thông tin phòng sau khi áp dụng mặc định không hợp lệ");
        return merged;
    }

    private static <T> T value(T override, T defaults) { return override != null ? override : defaults; }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static boolean negative(BigDecimal value) { return value != null && value.signum() < 0; }
}
