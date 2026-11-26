package com.tamdaptit.rentalmanagement.listing.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ListingSearchRequest(
    String roomTypeName,
    String provinceId,
    String wardId,
    RoomRentalStatus rentalStatus,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    List<String> amenityNames,
    @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
    @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
    @Positive BigDecimal radiusKm,
    @DecimalMin("-90") @DecimalMax("90") BigDecimal south,
    @DecimalMin("-90") @DecimalMax("90") BigDecimal north,
    @DecimalMin("-180") @DecimalMax("180") BigDecimal west,
    @DecimalMin("-180") @DecimalMax("180") BigDecimal east,
    @Size(max = 100) String keyword,
    @Pattern(regexp = "WHOLE|BY_ROOM") String rentalMode,
    @Size(max = 36) String propertyTypeId,
    @DecimalMin("0") BigDecimal minArea,
    @DecimalMin("0") BigDecimal maxArea,
    @Min(1) @Max(100) Integer minOccupants,
    @Min(0) @Max(200) Integer minFloor,
    @Min(0) @Max(200) Integer maxFloor,
    @DecimalMin("0") BigDecimal maxDeposit,
    @DecimalMin("0") BigDecimal maxElectricityPrice,
    @DecimalMin("0") BigDecimal maxWaterPrice,
    @DecimalMin("0") BigDecimal maxServiceFee
) {}
