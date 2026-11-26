package com.tamdaptit.rentalmanagement.listing.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingPhoneResponse;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.listing.service.ListingContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListingContactServiceImpl implements ListingContactService {
    private final ListingRepository listings;
    private final Clock subscriptionClock;
    private final JdbcTemplate jdbc;

    @Transactional
    @Override
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
            jdbc.update("""
                INSERT INTO listing_daily_stats(id,listing_id,stat_date,view_count,contact_count) VALUES(UUID(),?,?,0,1)
                ON DUPLICATE KEY UPDATE contact_count=COALESCE(contact_count,0)+1
                """, id, LocalDate.now(subscriptionClock));
        }

        return new ListingPhoneResponse(phone.trim());
    }
}
