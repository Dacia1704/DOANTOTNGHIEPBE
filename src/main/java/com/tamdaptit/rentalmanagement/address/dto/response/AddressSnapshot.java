package com.tamdaptit.rentalmanagement.address.dto.response;

import java.util.List;
import java.math.BigDecimal;

public record AddressSnapshot(List<ProvinceData> provinces) {
    public record ProvinceData(String code, String name, BigDecimal centroidLat,
                               BigDecimal centroidLng, List<WardData> wards) {}
    public record WardData(String code, String name, BigDecimal centroidLat,
                           BigDecimal centroidLng) {}
}
