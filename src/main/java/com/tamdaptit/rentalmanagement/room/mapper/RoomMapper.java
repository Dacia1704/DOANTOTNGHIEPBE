package com.tamdaptit.rentalmanagement.room.mapper;

import com.tamdaptit.rentalmanagement.room.dto.request.RoomRequest;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.entity.RoomAmenity;
import com.tamdaptit.rentalmanagement.room.entity.RoomImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RoomMapper {

    @Mapping(target = "landlordId", source = "room.landlord.id")
    @Mapping(target = "roomTypeId", source = "room.roomType.id")
    @Mapping(target = "roomTypeName", source = "room.roomType.name")
    @Mapping(target = "provinceId", source = "room.province.id")
    @Mapping(target = "provinceName", source = "room.province.name")
    @Mapping(target = "wardId", source = "room.ward.id")
    @Mapping(target = "wardName", source = "room.ward.name")
    @Mapping(target = "amenities", source = "roomAmenityList")
    @Mapping(target = "images", source = "roomImageList")
    RoomResponse toResponse(Room room, List<RoomAmenity> roomAmenityList, List<RoomImage> roomImageList);

    @Mapping(target = "id", source = "amenity.id")
    @Mapping(target = "name", source = "amenity.name")
    @Mapping(target = "icon", source = "amenity.icon")
    RoomResponse.AmenityResponse toAmenityResponse(RoomAmenity roomAmenity);

    RoomResponse.RoomImageResponse toImageResponse(RoomImage image);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "landlord", ignore = true)
    @Mapping(target = "roomType", ignore = true)
    @Mapping(target = "province", ignore = true)
    @Mapping(target = "ward", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromRequest(RoomRequest request, @MappingTarget Room room);
}
