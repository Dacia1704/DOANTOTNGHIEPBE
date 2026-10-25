package com.tamdaptit.rentalmanagement.subscription.mapper;

import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostPurchaseResponse;
import com.tamdaptit.rentalmanagement.subscription.entity.BoostServicePackageSubscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BoostPurchaseMapper {
    @Mapping(target = "boostPackageId", source = "boostPackage.id")
    BoostPurchaseResponse toResponse(BoostServicePackageSubscription boost);
}
