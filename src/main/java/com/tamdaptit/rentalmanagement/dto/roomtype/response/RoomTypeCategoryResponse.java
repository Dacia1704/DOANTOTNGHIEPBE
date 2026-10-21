package com.tamdaptit.rentalmanagement.dto.roomtype.response;

import java.util.List;
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
public class RoomTypeCategoryResponse {

    String id;
    String name;
    String description;
    String parentId;
    List<RoomSummaryResponse> rooms;
}
