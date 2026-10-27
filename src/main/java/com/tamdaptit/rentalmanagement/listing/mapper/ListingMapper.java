package com.tamdaptit.rentalmanagement.listing.mapper;

import com.tamdaptit.rentalmanagement.property.dto.response.PropertyResponse;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ListingMapper {
    @Mapping(target = "id", source = "listing.id")
    @Mapping(target = "createdAt", source = "listing.createdAt")
    @Mapping(target = "propertyId", source = "listing.property.id")
    @Mapping(target = "landlordId", source = "listing.property.landlord.id")
    @Mapping(target = "priorityLevel", source = "effectivePriority")
    @Mapping(target = "packagePriorityLevel", source = "selectedPriority")
    @Mapping(target = "property", source = "property")
    @Mapping(target = "propertyName", source = "listing.property.name")
    @Mapping(target = "rentalMode", source = "listing.property.rentalMode")
    @Mapping(target = "landlordName", source = "listing.property.landlord.fullName")
    ListingResponse toResponse(Listing listing, int effectivePriority, int selectedPriority,
            PropertyResponse property);
}
