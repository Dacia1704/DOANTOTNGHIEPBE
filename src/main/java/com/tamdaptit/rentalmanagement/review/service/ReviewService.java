package com.tamdaptit.rentalmanagement.review.service;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.review.dto.request.*;
import com.tamdaptit.rentalmanagement.review.dto.response.*;
import com.tamdaptit.rentalmanagement.review.entity.enums.ReviewType;

public interface ReviewService {
    ReviewResponse create(String actor, ReviewType type, ReviewCreateRequest body);
    ReviewEligibilityResponse eligibility(String actor, String agreementId);
    PageResponse<ReviewDetailResponse> mine(String actor, ReviewType type, int page, int size);
    PageResponse<ReceivedReviewResponse> received(String actor, ReviewType type, int page, int size);
    PageResponse<ReviewResponse> listing(String listingId, String roomId, int page, int size);
    ReviewSummaryResponse listingSummary(String listingId, String roomId);
    PageResponse<ReviewResponse> landlord(String landlordId, int page, int size);
    ReviewSummaryResponse landlordSummary(String landlordId);
    PageResponse<ReviewResponse> tenant(String actor, String tenantId, int page, int size);
    ReviewSummaryResponse tenantSummary(String actor, String tenantId);
    ReviewResponse reply(String actor, ReviewType type, String id, ReviewReplyRequest body);
    PageResponse<ReviewDetailResponse> admin(String actor, ReviewType type, Boolean hidden, String targetId, int page, int size);
    ReviewDetailResponse visibility(String actor, ReviewType type, String id, ReviewVisibilityRequest body);
}
