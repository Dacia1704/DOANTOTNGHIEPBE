package com.tamdaptit.rentalmanagement.room.dto.response;

import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RoomResponse(
        String id, String propertyId, String roomNumber, Integer floorNumber, String landlordId, String roomTypeId, String roomTypeName,
        BigDecimal price, BigDecimal electricityPrice, BigDecimal waterPrice,
        BigDecimal serviceFee, BigDecimal defaultDeposit, BigDecimal areaM2,
        Integer maxOccupants, String provinceId, String provinceName,
        String wardId, String wardName, String addressLine, BigDecimal latitude,
        BigDecimal longitude, RoomRentalStatus rentalStatus,
        List<AmenityResponse> amenities, List<RoomImageResponse> images,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public record AmenityResponse(String id, String name, String icon) {}
    public record RoomImageResponse(String id, String url, Integer sortOrder, LocalDateTime createdAt) {}
}
