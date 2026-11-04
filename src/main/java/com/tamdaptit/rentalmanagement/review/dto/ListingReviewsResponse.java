package com.tamdaptit.rentalmanagement.review.dto;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import java.time.LocalDateTime;
import java.util.List;

public record ListingReviewsResponse(Summary summary, PageResponse<Item> reviews) {
    public record Summary(Double averageRating, long totalReviews, Integer replyRate, List<Distribution> distribution) {}
    public record Distribution(int stars, long count) {}
    public record Item(String id, String authorName, int rating, String comment, String landlordReply,
            LocalDateTime createdAt, String roomNumber, boolean verifiedStay) {}
}
