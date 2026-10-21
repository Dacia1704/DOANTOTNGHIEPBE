package com.tamdaptit.rentalmanagement.service;

import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.dto.roomtype.request.RoomTypeRequest;
import com.tamdaptit.rentalmanagement.dto.roomtype.response.RoomTypeCategoryResponse;
import com.tamdaptit.rentalmanagement.dto.roomtype.response.RoomTypeResponse;
import java.util.List;

public interface RoomTypeService {

    PageResponse<RoomTypeResponse> getAll(int page, int size);

    RoomTypeResponse getById(String id);

    RoomTypeResponse create(RoomTypeRequest request);

    RoomTypeResponse update(String id, RoomTypeRequest request);

    void delete(String id);

    List<RoomTypeCategoryResponse> getCategoriesWithRooms();
}
