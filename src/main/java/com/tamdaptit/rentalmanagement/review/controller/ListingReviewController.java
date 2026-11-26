package com.tamdaptit.rentalmanagement.review.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.review.dto.ListingReviewsResponse;
import com.tamdaptit.rentalmanagement.review.service.ListingReviewService;
import com.tamdaptit.rentalmanagement.review.service.ListingReviewService.Target;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingReviewController {
    private final ListingReviewService reviews;

    @GetMapping(value = "/{id}/reviews", params = "target")
    public ApiResponse<ListingReviewsResponse> get(
        @PathVariable String id,
        @RequestParam(defaultValue = "ROOM") Target target,
        @RequestParam(required = false) String roomId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "4") int size
    ) {
        return ApiResponse.success(reviews.get(id, target, roomId, page, size));
    }
}
