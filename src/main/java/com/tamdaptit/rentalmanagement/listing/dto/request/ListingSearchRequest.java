package com.tamdaptit.rentalmanagement.listing.dto.request;

import java.math.BigDecimal;
import java.util.List;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;

public record ListingSearchRequest(String roomTypeName, String provinceId, String wardId,
        RoomRentalStatus rentalStatus, BigDecimal minPrice, BigDecimal maxPrice, List<String> amenityNames) {}
