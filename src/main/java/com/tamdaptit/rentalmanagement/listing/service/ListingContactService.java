package com.tamdaptit.rentalmanagement.listing.service;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingPhoneResponse;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListingContactService {
    private final ListingRepository listings;
    private final Clock subscriptionClock;

    @Transactional
    public ListingPhoneResponse revealPhone(String id, String viewerId) {
        var listing = listings.findPublishedContact(id, LocalDateTime.now(subscriptionClock))
                .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
        var landlord = listing.getProperty().getLandlord();
        var phone = landlord.getPhone();
        if (phone == null || phone.isBlank()) {
            return new ListingPhoneResponse(null);
        }
        // Count a successful reveal, including guests. An owner's own reveal is not a lead.
        // Atomic update prevents concurrent visitors from overwriting each other's count.
        if (!landlord.getId().equals(viewerId)) {
            listings.incrementContactCount(id);
        }
        return new ListingPhoneResponse(phone.trim());
    }
}
