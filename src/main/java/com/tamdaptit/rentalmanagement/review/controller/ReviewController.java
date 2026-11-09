package com.tamdaptit.rentalmanagement.review.controller;

import com.tamdaptit.rentalmanagement.common.dto.*;
import com.tamdaptit.rentalmanagement.review.dto.request.*;
import com.tamdaptit.rentalmanagement.review.dto.response.*;
import com.tamdaptit.rentalmanagement.review.entity.enums.ReviewType;
import com.tamdaptit.rentalmanagement.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService service;

    @GetMapping("/rental-agreements/{id}/review-eligibility")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD')")
    public ApiResponse<ReviewEligibilityResponse> eligibility(Principal actor,@PathVariable String id) {
        return ApiResponse.success(service.eligibility(actor.getName(),id));
    }

    @PostMapping("/reviews/rooms")
    @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<ReviewResponse> createROOM(Principal actor,@Valid @RequestBody ReviewCreateRequest body) {
        return ApiResponse.success(service.create(actor.getName(),ReviewType.ROOM,body));
    }

    @PostMapping("/reviews/landlords")
    @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<ReviewResponse> createLANDLORD(Principal actor,@Valid @RequestBody ReviewCreateRequest body) {
        return ApiResponse.success(service.create(actor.getName(),ReviewType.LANDLORD,body));
    }

    @PostMapping("/reviews/tenants")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ReviewResponse> createTENANT(Principal actor,@Valid @RequestBody ReviewCreateRequest body) {
        return ApiResponse.success(service.create(actor.getName(),ReviewType.TENANT,body));
    }

    @GetMapping("/reviews/mine")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD')")
    public ApiResponse<PageResponse<ReviewDetailResponse>> mine(Principal actor,@RequestParam ReviewType type,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.mine(actor.getName(),type,page,size));
    }

    @GetMapping("/landlord/reviews")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<ReceivedReviewResponse>> received(Principal actor,@RequestParam ReviewType type,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.received(actor.getName(),type,page,size));
    }

    @GetMapping({"/listings/{listingId}/reviews","/listings/{listingId}/rooms/{roomId}/reviews"})
    public ApiResponse<PageResponse<ReviewResponse>> listing(@PathVariable String listingId,
        @PathVariable(required=false) String roomId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.listing(listingId,roomId,page,size));
    }
    @GetMapping({"/listings/{listingId}/reviews/summary","/listings/{listingId}/rooms/{roomId}/reviews/summary"})
    public ApiResponse<ReviewSummaryResponse> listingSummary(@PathVariable String listingId,@PathVariable(required=false) String roomId) {
        return ApiResponse.success(service.listingSummary(listingId,roomId));
    }
    @GetMapping("/landlords/{id}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> landlord(@PathVariable String id,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.landlord(id,page,size));
    }
    @GetMapping("/landlords/{id}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> landlordSummary(@PathVariable String id) {
        return ApiResponse.success(service.landlordSummary(id));
    }
    @GetMapping("/tenants/{id}/reviews")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    public ApiResponse<PageResponse<ReviewResponse>> tenant(Principal actor,@PathVariable String id,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.tenant(actor.getName(),id,page,size));
    }
    @GetMapping("/tenants/{id}/reviews/summary")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    public ApiResponse<ReviewSummaryResponse> tenantSummary(Principal actor,@PathVariable String id) {
        return ApiResponse.success(service.tenantSummary(actor.getName(),id));
    }

    @PutMapping("/reviews/rooms/{id}/reply")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ReviewResponse> replyROOM(Principal actor,@PathVariable String id,@Valid @RequestBody ReviewReplyRequest body) {
        return ApiResponse.success(service.reply(actor.getName(),ReviewType.ROOM,id,body));
    }

    @PutMapping("/reviews/landlords/{id}/reply")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ReviewResponse> replyLANDLORD(Principal actor,@PathVariable String id,@Valid @RequestBody ReviewReplyRequest body) {
        return ApiResponse.success(service.reply(actor.getName(),ReviewType.LANDLORD,id,body));
    }

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasAuthority('REVIEW_READ')")
    public ApiResponse<PageResponse<ReviewDetailResponse>> admin(Principal actor,@RequestParam ReviewType type,
        @RequestParam(required=false) Boolean hidden,@RequestParam(required=false) String targetId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.admin(actor.getName(),type,hidden,targetId,page,size));
    }
    @PatchMapping("/admin/reviews/{type}/{id}/visibility")
    @PreAuthorize("hasAuthority('REVIEW_MODERATE')")
    public ApiResponse<ReviewDetailResponse> visibility(Principal actor,@PathVariable ReviewType type,
        @PathVariable String id,@Valid @RequestBody ReviewVisibilityRequest body) {
        return ApiResponse.success(service.visibility(actor.getName(),type,id,body));
    }
}
