package com.tamdaptit.rentalmanagement.rental.dto.response;

import java.math.BigDecimal;

/** Room data returned only after the request's participant access check. */
public record RentalRequestRoomResponse(
        String listingTitle, String landlordName, String rentalMode, String roomTypeName,
        Integer floorNumber, BigDecimal areaM2, Integer maxOccupants,
        String fullAddress, BigDecimal latitude, BigDecimal longitude,
        BigDecimal price, BigDecimal deposit, BigDecimal electricityPrice,
        BigDecimal waterPrice, BigDecimal serviceFee, String priceSource) {}
