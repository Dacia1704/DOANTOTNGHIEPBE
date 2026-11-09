package com.tamdaptit.rentalmanagement.review.service;

import com.tamdaptit.rentalmanagement.review.service.impl.ListingReviewServiceImpl;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.property.entity.Property;
import com.tamdaptit.rentalmanagement.review.dto.response.*;
import com.tamdaptit.rentalmanagement.review.entity.enums.ReviewType;
import com.tamdaptit.rentalmanagement.review.service.ListingReviewService.Target;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingReviewServiceTest {
    private final ReviewService reviews = mock(ReviewService.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ListingReviewService service = new ListingReviewServiceImpl(reviews, listings, clock);

    private PageResponse<ReviewResponse> page() {
        return PageResponse.<ReviewResponse>builder().currentPage(1).pageSize(4).totalPages(2).totalElements(5)
                .data(List.of(new ReviewResponse("review", ReviewType.ROOM, 5, "Clean room", "Thank you",
                        "Tenant", null, LocalDateTime.now(clock), null, true))).build();
    }

    @Test void roomAdapterUsesCanonicalReviewsAndKeepsTheExistingUiContract() {
        when(reviews.listing("post", "room", 1, 4)).thenReturn(page());
        when(reviews.listingSummary("post", "room")).thenReturn(new ReviewSummaryResponse(
                5, new BigDecimal("4.8"), Map.of(5, 4L, 4, 1L), 40));
        var result = service.get("post", Target.ROOM, "room", 1, 4);
        assertEquals(4.8, result.summary().averageRating());
        assertEquals(40, result.summary().replyRate());
        assertEquals(List.of(4L, 1L, 0L, 0L, 0L), result.summary().distribution().stream().map(d -> d.count()).toList());
        assertEquals(1, result.reviews().getCurrentPage());
        assertEquals(5, result.reviews().getTotalElements());
        assertTrue(result.reviews().getData().getFirst().verifiedStay());
        assertEquals("Thank you", result.reviews().getData().getFirst().landlordReply());
        verifyNoInteractions(listings);
    }

    @Test void landlordAdapterResolvesThePublicListingsOwnerAndIgnoresTheSelectedRoom() {
        var owner = User.builder().id("owner").build();
        var property = Property.builder().landlord(owner).build();
        when(listings.findPublicForReview("post", LocalDateTime.now(clock)))
                .thenReturn(Optional.of(Listing.builder().property(property).build()));
        when(reviews.landlord("owner", 0, 4)).thenReturn(page());
        when(reviews.landlordSummary("owner")).thenReturn(new ReviewSummaryResponse(0, null, Map.of(), null));
        var result = service.get("post", Target.LANDLORD, "unrelated-room", 0, 4);
        assertNull(result.summary().averageRating());
        assertNull(result.summary().replyRate());
        verify(reviews).landlord("owner", 0, 4);
        verify(reviews).landlordSummary("owner");
        verifyNoMoreInteractions(reviews);
    }

    @Test void hiddenListingCannotBeUsedToResolveLandlordReviews() {
        when(listings.findPublicForReview("post", LocalDateTime.now(clock))).thenReturn(Optional.empty());
        assertEquals(ErrorCode.LISTING_NOT_FOUND,
                assertThrows(AppException.class, () -> service.get("post", Target.LANDLORD, null, 0, 4)).getErrorCode());
        verifyNoInteractions(reviews);
    }

    @Test void adapterPreservesCanonicalAccessChecks() {
        when(reviews.listing("post", "foreign", 0, 4)).thenThrow(new AppException(ErrorCode.ROOM_NOT_FOUND));
        assertEquals(ErrorCode.ROOM_NOT_FOUND,
                assertThrows(AppException.class, () -> service.get("post", Target.ROOM, "foreign", 0, 4)).getErrorCode());
        verify(reviews, never()).listingSummary(anyString(), anyString());
    }
}
