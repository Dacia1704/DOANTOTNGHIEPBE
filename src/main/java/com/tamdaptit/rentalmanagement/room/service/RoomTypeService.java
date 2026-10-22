package com.tamdaptit.rentalmanagement.room.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.room.dto.request.RoomTypeRequest;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomTypeCategoryResponse;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomTypeResponse;
import java.util.List;

public interface RoomTypeService {

    PageResponse<RoomTypeResponse> getAll(int page, int size);

    RoomTypeResponse getById(String id);

    RoomTypeResponse create(RoomTypeRequest request);

    RoomTypeResponse update(String id, RoomTypeRequest request);

    void delete(String id);

    List<RoomTypeCategoryResponse> getCategoriesWithRooms();
}
