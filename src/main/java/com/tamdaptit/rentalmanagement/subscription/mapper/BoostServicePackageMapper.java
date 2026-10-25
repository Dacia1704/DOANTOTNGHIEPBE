package com.tamdaptit.rentalmanagement.subscription.mapper;

import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostServicePackageResponse;
import com.tamdaptit.rentalmanagement.subscription.entity.BoostServicePackage;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BoostServicePackageMapper {
    BoostServicePackage toEntity(BoostServicePackageRequest request);

    void update(BoostServicePackageRequest request, @MappingTarget BoostServicePackage entity);

    BoostServicePackageResponse toResponse(BoostServicePackage entity);
}
