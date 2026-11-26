package com.tamdaptit.rentalmanagement.listing.controller;

import java.security.Principal;
import java.util.List;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.discovery.ListingDiscoveryService;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingContentRequest;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingPriorityRequest;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingRejectionRequest;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingRequest;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingSearchRequest;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingPriceStatisticsResponse;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {
    private final ListingService service;
    private final ListingDiscoveryService discovery;

    @GetMapping
    public ApiResponse<PageResponse<ListingResponse>> published(
        Principal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @Valid @ModelAttribute ListingSearchRequest request,
        @RequestParam(defaultValue = "recommended") String sort,
        @RequestParam(required = false) String rankingSession
    ) {
        return ApiResponse.success(
            discovery.search(principal == null ? null : principal.getName(), page, size, request, sort, rankingSession)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ListingResponse> publishedById(@PathVariable String id) {
        return ApiResponse.success(service.getPublishedById(id));
    }

    @GetMapping("/map")
    public ApiResponse<List<ListingResponse>> map(
        Principal principal,
        @Valid @ModelAttribute ListingSearchRequest request,
        @RequestParam(defaultValue = "recommended") String sort
    ) {
        return ApiResponse.success(discovery.map(principal == null ? null : principal.getName(), request, sort));
    }

    @GetMapping("/price-statistics")
    public ApiResponse<List<ListingPriceStatisticsResponse>> priceStatistics(
        @RequestParam(required = false) String provinceId,
        @RequestParam(required = false) String wardId
    ) {
        return ApiResponse.success(service.priceStatistics(provinceId, wardId));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<ListingResponse>> mine(
        Principal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(service.getMine(principal.getName(), page, size));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ListingResponse>> admin(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) ListingStatus status
    ) {
        return ApiResponse.success(service.getAllForAdmin(page, size, status));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ListingResponse>> pending(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(service.getPending(page, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> create(Principal principal, @Valid @RequestBody ListingRequest request) {
        return ApiResponse.success(service.create(principal.getName(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> update(
        Principal principal,
        @PathVariable String id,
        @Valid @RequestBody ListingContentRequest request
    ) {
        return ApiResponse.success(service.updateContent(principal.getName(), id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> submit(Principal principal, @PathVariable String id) {
        return ApiResponse.success(service.submit(principal.getName(), id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ListingResponse> approve(Principal principal, @PathVariable String id) {
        return ApiResponse.success(service.approve(principal.getName(), id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ListingResponse> reject(
        Principal principal,
        @PathVariable String id,
        @Valid @RequestBody ListingRejectionRequest request
    ) {
        return ApiResponse.success(service.reject(principal.getName(), id, request.reason()));
    }

    @PostMapping("/{id}/hide")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> hide(Principal principal, @PathVariable String id) {
        return ApiResponse.success(service.hide(principal.getName(), id));
    }

    @PutMapping("/{id}/priority")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> priority(
        Principal principal,
        @PathVariable String id,
        @Valid @RequestBody ListingPriorityRequest request
    ) {
        return ApiResponse.success(service.setPriority(principal.getName(), id, request.level()));
    }
}
