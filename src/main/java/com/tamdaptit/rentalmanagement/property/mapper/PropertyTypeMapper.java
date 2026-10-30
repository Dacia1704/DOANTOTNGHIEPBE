package com.tamdaptit.rentalmanagement.property.mapper;

import com.tamdaptit.rentalmanagement.property.dto.request.PropertyTypeRequest;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyTypeResponse;
import com.tamdaptit.rentalmanagement.property.entity.PropertyType;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PropertyTypeMapper {
    PropertyTypeResponse toResponse(PropertyType type);
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PropertyType toEntity(PropertyTypeRequest request);
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(PropertyTypeRequest request, @MappingTarget PropertyType type);
}
