package com.tamdaptit.rentalmanagement.review.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.review.dto.ListingReviewsResponse;
import com.tamdaptit.rentalmanagement.review.dto.ListingReviewsResponse.*;
import com.tamdaptit.rentalmanagement.review.dto.response.ReviewResponse;
import com.tamdaptit.rentalmanagement.review.dto.response.ReviewSummaryResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Response adapter for the listing UI. All review access and calculations use ReviewService. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingReviewService {
    public enum Target { ROOM, LANDLORD }
    private final ReviewService reviews;
    private final ListingRepository listings;
    private final Clock subscriptionClock;

    public ListingReviewsResponse get(String id, Target target, String roomId, int page, int size) {
        PageResponse<ReviewResponse> result;
        ReviewSummaryResponse summary;
        if (target == Target.LANDLORD) {
            var listing = listings.findPublicForReview(id, LocalDateTime.now(subscriptionClock))
                    .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
            var ownerId = listing.getProperty().getLandlord().getId();
            result = reviews.landlord(ownerId, page, size);
            summary = reviews.landlordSummary(ownerId);
        } else {
            result = reviews.listing(id, roomId, page, size);
            summary = reviews.listingSummary(id, roomId);
        }
        var distribution = IntStream.iterate(5, n -> n > 0, n -> n - 1)
                .mapToObj(stars -> new Distribution(stars, summary.distribution().getOrDefault(stars, 0L))).toList();
        var adapted = new Summary(summary.averageRating() == null ? null : summary.averageRating().doubleValue(),
                summary.totalReviews(), summary.replyRate(), distribution);
        return new ListingReviewsResponse(adapted, PageResponse.<Item>builder()
                .currentPage(result.getCurrentPage()).pageSize(result.getPageSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getData().stream().map(r -> new Item(r.id(),
                        r.authorName() == null || r.authorName().isBlank() ? "Người thuê" : r.authorName(),
                        r.rating(), r.comment(), r.landlordReply(), r.createdAt(), null, r.verifiedStay())).toList())
                .build());
    }
}
