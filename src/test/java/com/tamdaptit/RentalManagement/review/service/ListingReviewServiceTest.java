package com.tamdaptit.rentalmanagement.review.service;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.property.entity.Property;
import com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.review.entity.RoomReview;
import com.tamdaptit.rentalmanagement.review.repository.*;
import com.tamdaptit.rentalmanagement.review.service.ListingReviewService.Target;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingReviewServiceTest {
    private final ListingRepository listings = mock(ListingRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomReviewRepository reviews = mock(RoomReviewRepository.class);
    private final LandlordReviewRepository landlords = mock(LandlordReviewRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ListingReviewService service = new ListingReviewService(listings, rooms, reviews, landlords, clock);
    private final User owner = User.builder().id("owner").build();
    private final Property property = Property.builder().id("property").landlord(owner).build();

    private RatingBucket bucket(int rating, long count, long replied) {
        var result = mock(RatingBucket.class);
        when(result.getRating()).thenReturn(rating);
        when(result.getTotal()).thenReturn(count);
        when(result.getReplied()).thenReturn(replied);
        return result;
    }

    private void published() {
        when(listings.findPublishedContact("post", LocalDateTime.now(clock)))
                .thenReturn(Optional.of(Listing.builder().id("post").property(property).build()));
    }

    @Test void weightedSummaryIncludesAllRatingsRatherThanJustTheCurrentPage() {
        var result = ListingReviewService.summary(List.of(bucket(5, 2, 1), bucket(3, 1, 1)));
        assertEquals(4.33, result.averageRating());
        assertEquals(3, result.totalReviews());
        assertEquals(67, result.replyRate());
        assertEquals(List.of(2L, 0L, 1L, 0L, 0L), result.distribution().stream().map(d -> d.count()).toList());
    }

    @Test void emptySummaryHasNoInventedRatingOrResponseRate() {
        var result = ListingReviewService.summary(List.of());
        assertNull(result.averageRating());
        assertNull(result.replyRate());
        assertEquals(0, result.totalReviews());
        assertEquals(5, result.distribution().size());
    }

    @Test void hiddenOrExpiredListingDoesNotExposeReviews() {
        when(listings.findPublishedContact("post", LocalDateTime.now(clock))).thenReturn(Optional.empty());
        assertEquals(ErrorCode.LISTING_NOT_FOUND,
                assertThrows(AppException.class, () -> service.get("post", Target.LANDLORD, null, 0, 4)).getErrorCode());
        verifyNoInteractions(reviews, landlords);
    }

    @Test void cannotReadARoomOutsideTheListingsProperty() {
        published();
        assertEquals(ErrorCode.ROOM_NOT_FOUND,
                assertThrows(AppException.class, () -> service.get("post", Target.ROOM, "foreign", 0, 4)).getErrorCode());
        verifyNoInteractions(reviews, landlords);
    }

    @Test void roomReviewUsesTheSelectedRoomAndConfirmsOnlyMatchingRentalContracts() {
        published();
        when(rooms.existsByIdAndProperty_Id("room", "property")).thenReturn(true);
        var tenant = User.builder().id("tenant").fullName("Người thuê A").build();
        var room = Room.builder().id("room").roomNumber("101").property(property).build();
        var agreement = RentalAgreement.builder().room(room).tenant(tenant).landlord(owner)
                .status(RentalAgreementStatus.ACTIVE).startDate(LocalDate.now(clock).minusMonths(1)).build();
        var review = RoomReview.builder().id("review").room(room).tenant(tenant).rentalAgreement(agreement)
                .rating(5).comment("Phòng sạch").landlordReply("Cảm ơn bạn").build();
        var summary = List.of(bucket(5, 1, 1));
        when(reviews.summarize("property", "room")).thenReturn(summary);
        when(reviews.findVisible(eq("property"), eq("room"), any())).thenReturn(new PageImpl<>(List.of(review)));
        var result = service.get("post", Target.ROOM, "room", 0, 4);
        assertEquals("Người thuê A", result.reviews().getData().getFirst().authorName());
        assertTrue(result.reviews().getData().getFirst().verifiedStay());
        assertEquals("Cảm ơn bạn", result.reviews().getData().getFirst().landlordReply());
        agreement.setTenant(User.builder().id("different-tenant").build());
        assertFalse(service.get("post", Target.ROOM, "room", 0, 4).reviews().getData().getFirst().verifiedStay());
        verifyNoInteractions(landlords);
    }
}
