package com.tamdaptit.rentalmanagement.subscription.mapper;

import com.tamdaptit.rentalmanagement.subscription.dto.request.ServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServicePackagePriceRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServicePackageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServicePackagePriceResponse;
import com.tamdaptit.rentalmanagement.subscription.entity.ServicePackage;
import com.tamdaptit.rentalmanagement.subscription.entity.ServicePackagePrice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ServicePackageMapper {
    ServicePackage toEntity(ServicePackageRequest request);

    void update(ServicePackageRequest request, @MappingTarget ServicePackage entity);

    ServicePackageResponse toResponse(ServicePackage entity);

    ServicePackagePrice toEntity(ServicePackagePriceRequest request);

    void update(ServicePackagePriceRequest request, @MappingTarget ServicePackagePrice entity);

    @Mapping(target = "packageId", source = "packageEntity.id")
    ServicePackagePriceResponse toResponse(ServicePackagePrice entity);
}
