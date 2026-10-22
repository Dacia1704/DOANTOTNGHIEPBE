package com.tamdaptit.rentalmanagement.room.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomTypeRequest(
        @NotBlank(message = "Tên loại phòng không được để trống")
        @Size(max = 100, message = "Tên loại phòng không được vượt quá 100 ký tự")
        String name,

        String description,

        String parentId) {
}
