package com.tamdaptit.rentalmanagement.dto.roomtype.response;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomTypeResponse {

    String id;
    String name;
    String description;
    String parentId;
    String parentName;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
