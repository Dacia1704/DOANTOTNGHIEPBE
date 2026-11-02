package com.tamdaptit.rentalmanagement.listing.dto.request;

import java.math.BigDecimal;
import java.util.List;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

public record ListingSearchRequest(String roomTypeName, String provinceId, String wardId,
        RoomRentalStatus rentalStatus, BigDecimal minPrice, BigDecimal maxPrice, List<String> amenityNames,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Positive BigDecimal radiusKm,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal south,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal north,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal west,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal east) {}
