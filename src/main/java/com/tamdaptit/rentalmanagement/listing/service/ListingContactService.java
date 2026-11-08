package com.tamdaptit.rentalmanagement.listing.service;

import com.tamdaptit.rentalmanagement.listing.dto.response.ListingPhoneResponse;

public interface ListingContactService {
    ListingPhoneResponse revealPhone(String id, String viewerId);
}
