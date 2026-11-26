package com.tamdaptit.rentalmanagement.address.dto.response;

import java.math.BigDecimal;

public record ProvinceResponse(
    String id,
    String code,
    String name,
    BigDecimal centroidLat,
    BigDecimal centroidLng
) {}
