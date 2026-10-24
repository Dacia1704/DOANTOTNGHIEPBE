package com.tamdaptit.rentalmanagement.subscription.mapper;

import com.tamdaptit.rentalmanagement.subscription.dto.response.*;
import com.tamdaptit.rentalmanagement.subscription.entity.ServiceSubscriptionOrder;
import com.tamdaptit.rentalmanagement.subscription.entity.ServiceSubscriptionPeriod;
import com.tamdaptit.rentalmanagement.subscription.entity.ServicePackageSubscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ServiceSubscriptionMapper {
    @Mapping(target = "packageId", source = "packageEntity.id")
    ServiceSubscriptionResponse toResponse(ServicePackageSubscription subscription);

    @Mapping(target = "landlordId", source = "landlord.id")
    @Mapping(target = "packageId", source = "packageEntity.id")
    @Mapping(target = "priceOptionId", source = "priceOption.id")
    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "previousSubscriptionId", source = "previousSubscription.id")
    @Mapping(target = "rights", source = ".")
    ServiceSubscriptionOrderResponse toResponse(ServiceSubscriptionOrder order);

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "rights", source = ".")
    ServiceSubscriptionPeriodResponse toResponse(ServiceSubscriptionPeriod period);

    @Mapping(target = "packageCode", source = "packageCodeSnapshot")
    @Mapping(target = "packageName", source = "packageNameSnapshot")
    @Mapping(target = "maxListings", source = "maxListingsSnapshot")
    @Mapping(target = "highPrioritySlots", source = "highPrioritySlotsSnapshot")
    @Mapping(target = "lowPrioritySlots", source = "lowPrioritySlotsSnapshot")
    @Mapping(target = "defaultPriorityLevel", source = "defaultPriorityLevelSnapshot")
    @Mapping(target = "hasStats", source = "hasStatsSnapshot")
    SubscriptionRightsResponse toRights(ServiceSubscriptionOrder order);

    @Mapping(target = "packageCode", source = "packageCodeSnapshot")
    @Mapping(target = "packageName", source = "packageNameSnapshot")
    @Mapping(target = "maxListings", source = "maxListingsSnapshot")
    @Mapping(target = "highPrioritySlots", source = "highPrioritySlotsSnapshot")
    @Mapping(target = "lowPrioritySlots", source = "lowPrioritySlotsSnapshot")
    @Mapping(target = "defaultPriorityLevel", source = "defaultPriorityLevelSnapshot")
    @Mapping(target = "hasStats", source = "hasStatsSnapshot")
    SubscriptionRightsResponse toRights(ServiceSubscriptionPeriod period);
}
