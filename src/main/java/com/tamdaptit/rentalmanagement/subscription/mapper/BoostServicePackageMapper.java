package com.tamdaptit.rentalmanagement.subscription.mapper;

import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostServicePackageResponse;
import com.tamdaptit.rentalmanagement.subscription.entity.BoostServicePackage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BoostServicePackageMapper {
    @Mapping(target = "priorityLevel", constant = "2")
    BoostServicePackage toEntity(BoostServicePackageRequest request);

    @Mapping(target = "priorityLevel", constant = "2")
    void update(BoostServicePackageRequest request, @MappingTarget BoostServicePackage entity);

    BoostServicePackageResponse toResponse(BoostServicePackage entity);
}
