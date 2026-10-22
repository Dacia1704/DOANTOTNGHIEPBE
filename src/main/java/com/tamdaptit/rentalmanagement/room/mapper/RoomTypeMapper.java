package com.tamdaptit.rentalmanagement.room.mapper;

import com.tamdaptit.rentalmanagement.room.dto.response.RoomSummaryResponse;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomTypeCategoryResponse;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomTypeResponse;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.entity.RoomType;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoomTypeMapper {

    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "parentName", source = "parent.name")
    RoomTypeResponse toResponse(RoomType roomType);

    @Named("roomSummary")
    RoomSummaryResponse toRoomSummary(Room room);

    @Mapping(target = "parentId", source = "roomType.parent.id")
    @Mapping(target = "rooms", source = "rooms")
    RoomTypeCategoryResponse toCategoryResponse(RoomType roomType, List<Room> rooms);
}