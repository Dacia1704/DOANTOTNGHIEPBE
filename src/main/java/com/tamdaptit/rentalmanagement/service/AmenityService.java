package com.tamdaptit.rentalmanagement.service;

import com.tamdaptit.rentalmanagement.dto.amenity.request.AmenityRequest;
import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.entity.Amenity;

public interface AmenityService {

    PageResponse<Amenity> getAll(int page, int size);

    Amenity getById(String id);

    Amenity create(AmenityRequest request);

    Amenity update(String id, AmenityRequest request);

    void delete(String id);
}
