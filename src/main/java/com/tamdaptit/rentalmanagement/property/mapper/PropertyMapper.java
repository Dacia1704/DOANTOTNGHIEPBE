package com.tamdaptit.rentalmanagement.property.mapper;

import java.util.List;

import com.tamdaptit.rentalmanagement.property.dto.request.CreatePropertyRequest;
import com.tamdaptit.rentalmanagement.property.dto.request.DefaultRoomRequest;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyResponse;
import com.tamdaptit.rentalmanagement.property.entity.Property;
import com.tamdaptit.rentalmanagement.property.entity.PropertyAmenity;
import com.tamdaptit.rentalmanagement.property.entity.PropertyImage;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PropertyMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Property toEntity(CreatePropertyRequest request);

    // Values have already been merged and validated by the service.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rentalStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Room toRoom(DefaultRoomRequest values);

    @Mapping(target = "landlordId", source = "property.landlord.id")
    @Mapping(target = "propertyTypeId", source = "property.propertyType.id")
    @Mapping(target = "propertyTypeName", source = "property.propertyType.name")
    @Mapping(target = "provinceId", source = "property.province.id")
    @Mapping(target = "provinceName", source = "property.province.name")
    @Mapping(target = "wardId", source = "property.ward.id")
    @Mapping(target = "wardName", source = "property.ward.name")
    @Mapping(target = "amenities", source = "amenities")
    @Mapping(target = "images", source = "images")
    @Mapping(target = "rooms", source = "rooms")
    PropertyResponse toResponse(
        Property property,
        List<PropertyAmenity> amenities,
        List<PropertyImage> images,
        List<RoomResponse> rooms
    );

    @Mapping(target = "id", source = "amenity.id")
    @Mapping(target = "name", source = "amenity.name")
    @Mapping(target = "icon", source = "amenity.icon")
    RoomResponse.AmenityResponse toAmenityResponse(PropertyAmenity amenity);

    PropertyResponse.PropertyImageResponse toImageResponse(PropertyImage image);
}
