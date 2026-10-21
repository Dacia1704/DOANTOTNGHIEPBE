package com.tamdaptit.rentalmanagement.dto.address;

import com.tamdaptit.rentalmanagement.entity.Ward;

public record WardResponse(String id, String code, String name,
                           String provinceId, String provinceCode, String provinceName) {
    public static WardResponse from(Ward ward) {
        var province = ward.getProvince();
        return new WardResponse(ward.getId(), ward.getCode(), ward.getName(),
                province.getId(), province.getCode(), province.getName());
    }
}
