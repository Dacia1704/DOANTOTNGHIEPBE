package com.tamdaptit.rentalmanagement.property.dto.response;

import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PropertyResponse(String id, String landlordId, String name,
        String propertyTypeId, String propertyTypeName, PropertyRentalMode rentalMode, Integer totalFloors,
        String provinceId, String provinceName, String wardId, String wardName, String addressLine,
        BigDecimal latitude, BigDecimal longitude, List<RoomResponse.AmenityResponse> amenities,
        List<PropertyImageResponse> images, List<RoomResponse> rooms,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public record PropertyImageResponse(String id, String url, Integer sortOrder, LocalDateTime createdAt) {}
}
