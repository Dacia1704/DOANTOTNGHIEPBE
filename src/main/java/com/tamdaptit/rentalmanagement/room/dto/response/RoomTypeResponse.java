package com.tamdaptit.rentalmanagement.room.dto.response;

import java.time.LocalDateTime;

public record RoomTypeResponse(
        String id,
        String name,
        String description,
        String parentId,
        String parentName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
