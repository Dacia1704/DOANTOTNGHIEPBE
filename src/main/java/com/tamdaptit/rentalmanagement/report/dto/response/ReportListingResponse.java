package com.tamdaptit.rentalmanagement.report.dto.response;

import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import java.time.LocalDateTime;

/** Report-scoped context, including hidden and expired listings; contains no private owner data. */
public record ReportListingResponse(String id,String title,String description,ListingStatus status,
    LocalDateTime publishedAt,LocalDateTime expiresAt,String propertyId,String propertyName,
    String addressLine,String wardName,String provinceName,String landlordId) {
    public static ReportListingResponse from(Listing listing) {
        if(listing==null) return null;
        var property=listing.getProperty();
        return new ReportListingResponse(listing.getId(),listing.getTitle(),listing.getDescription(),listing.getStatus(),
            listing.getPublishedAt(),listing.getExpiresAt(),property.getId(),property.getName(),
            property.getAddressLine(),property.getWard().getName(),property.getProvince().getName(),property.getLandlord().getId());
    }
}
