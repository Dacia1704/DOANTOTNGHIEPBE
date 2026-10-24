package com.tamdaptit.rentalmanagement.listing.mapper;

import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ListingMapper {
    @Mapping(target = "roomId", source = "listing.room.id")
    @Mapping(target = "landlordId", source = "listing.room.landlord.id")
    @Mapping(target = "priorityLevel", source = "effectivePriority")
    @Mapping(target = "packagePriorityLevel", source = "selectedPriority")
    ListingResponse toResponse(Listing listing, int effectivePriority, int selectedPriority);
}
