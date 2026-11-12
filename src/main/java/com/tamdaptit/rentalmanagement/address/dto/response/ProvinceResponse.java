package com.tamdaptit.rentalmanagement.address.dto.response;

public record ProvinceResponse(String id, String code, String name,
                               java.math.BigDecimal centroidLat, java.math.BigDecimal centroidLng) {
}
