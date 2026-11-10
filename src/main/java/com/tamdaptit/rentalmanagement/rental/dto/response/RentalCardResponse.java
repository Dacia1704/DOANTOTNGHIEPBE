package com.tamdaptit.rentalmanagement.rental.dto.response;

import java.math.BigDecimal;

public record RentalCardResponse(String thumbnailUrl, String fullAddress, String roomTypeName,
        BigDecimal areaM2, String landlordName, BigDecimal price, String listingTitle) {}
