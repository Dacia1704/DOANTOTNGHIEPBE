package com.tamdaptit.rentalmanagement.property.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.property.dto.request.PropertyTypeRequest;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyTypeResponse;

public interface PropertyTypeService {
    PageResponse<PropertyTypeResponse> getAll(int page, int size);
    PropertyTypeResponse getById(String id);
    PropertyTypeResponse create(PropertyTypeRequest request);
    PropertyTypeResponse update(String id, PropertyTypeRequest request);
    void delete(String id);
}
