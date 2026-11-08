package com.tamdaptit.rentalmanagement.listing;

import com.tamdaptit.rentalmanagement.listing.service.impl.ListingContactServiceImpl;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.listing.service.ListingContactService;
import com.tamdaptit.rentalmanagement.property.entity.Property;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingContactServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ListingRepository listings = mock(ListingRepository.class);
    private final ListingContactService service = new ListingContactServiceImpl(listings, clock);

    private void publishedWithPhone(String phone) {
        var owner = User.builder().id("owner").phone(phone).build();
        var listing = Listing.builder().id("post").property(Property.builder().landlord(owner).build()).build();
        when(listings.findPublishedContact("post", LocalDateTime.now(clock))).thenReturn(Optional.of(listing));
    }

    @Test void guestRevealReturnsPhoneAndIncrementsExactlyOnce() {
        publishedWithPhone(" 0901234567 ");
        assertEquals("0901234567", service.revealPhone("post", null).phone());
        verify(listings).incrementContactCount("post");
    }

    @Test void authenticatedVisitorIsCounted() {
        publishedWithPhone("0901234567");
        service.revealPhone("post", "tenant");
        verify(listings).incrementContactCount("post");
    }

    @Test void ownerRevealIsNotCounted() {
        publishedWithPhone("0901234567");
        assertEquals("0901234567", service.revealPhone("post", "owner").phone());
        verify(listings, never()).incrementContactCount(anyString());
    }

    @Test void missingPhoneIsNotCounted() {
        for (String phone : new String[] { null, "", "   " }) {
            publishedWithPhone(phone);
            assertNull(service.revealPhone("post", null).phone());
        }
        verify(listings, never()).incrementContactCount(anyString());
    }

    @Test void unavailableListingDoesNotExposePhoneOrIncrement() {
        when(listings.findPublishedContact("hidden", LocalDateTime.now(clock))).thenReturn(Optional.empty());
        var error = assertThrows(AppException.class, () -> service.revealPhone("hidden", null));
        assertEquals(ErrorCode.LISTING_NOT_FOUND, error.getErrorCode());
        verify(listings, never()).incrementContactCount(anyString());
    }
}
