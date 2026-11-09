package com.tamdaptit.rentalmanagement.listing.dto.response;

public record ListingPriceStatisticsResponse(String areaId, String areaName,
        Double averagePrice, Long roomCount) {
}
