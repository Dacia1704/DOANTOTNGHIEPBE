package com.tamdaptit.rentalmanagement.room.dto.response;

import java.util.List;

public record RoomTypeCategoryResponse(
        String id,
        String name,
        String description,
        String parentId,
        List<RoomSummaryResponse> rooms) {
}
