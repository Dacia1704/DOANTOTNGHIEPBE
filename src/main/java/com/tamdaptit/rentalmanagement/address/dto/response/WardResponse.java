package com.tamdaptit.rentalmanagement.address.dto.response;

import java.math.BigDecimal;

import com.tamdaptit.rentalmanagement.address.entity.Ward;

public record WardResponse(
    String id,
    String code,
    String name,
    String provinceId,
    String provinceCode,
    String provinceName,
    BigDecimal centroidLat,
    BigDecimal centroidLng
) {
    public static WardResponse from(Ward ward) {
        var province = ward.getProvince();
        return new WardResponse(
            ward.getId(),
            ward.getCode(),
            ward.getName(),
            province.getId(),
            province.getCode(),
            province.getName(),
            ward.getCentroidLat(),
            ward.getCentroidLng()
        );
    }
}
