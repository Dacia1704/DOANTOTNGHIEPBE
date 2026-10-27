package com.tamdaptit.rentalmanagement.property.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.property.dto.request.CreatePropertyRequest;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyResponse;

public interface PropertyService {
    PropertyResponse create(String userId, CreatePropertyRequest request);
    PropertyResponse get(String userId, String id);
    PageResponse<PropertyResponse> mine(String userId, int page, int size);
}
