package com.tamdaptit.rentalmanagement.review.service;

import com.tamdaptit.rentalmanagement.review.dto.ListingReviewsResponse;

public interface ListingReviewService {
    enum Target { ROOM, LANDLORD }
    ListingReviewsResponse get(String id, Target target, String roomId, int page, int size);
}
