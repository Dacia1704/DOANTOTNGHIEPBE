package com.tamdaptit.rentalmanagement.room.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.room.dto.request.AmenityRequest;
import com.tamdaptit.rentalmanagement.room.entity.Amenity;

public interface AmenityService {

    PageResponse<Amenity> getAll(int page, int size);

    Amenity getById(String id);

    Amenity create(AmenityRequest request);

    Amenity update(String id, AmenityRequest request);

    void delete(String id);
}
