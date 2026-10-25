package com.tamdaptit.rentalmanagement.listing.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.listing.dto.request.*;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {
    private final ListingService service;

    @GetMapping
    public ApiResponse<PageResponse<ListingResponse>> published(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getPublished(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ListingResponse> publishedById(@PathVariable String id) {
        return ApiResponse.success(service.getPublishedById(id));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<ListingResponse>> mine(Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getMine(principal.getName(), page, size));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ListingResponse>> pending(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getPending(page, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> create(Principal principal, @Valid @RequestBody ListingRequest request) {
        return ApiResponse.success(service.create(principal.getName(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> update(Principal principal, @PathVariable String id,
            @Valid @RequestBody ListingContentRequest request) {
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
    public ApiResponse<ListingResponse> reject(Principal principal, @PathVariable String id,
            @Valid @RequestBody ListingRejectionRequest request) {
        return ApiResponse.success(service.reject(principal.getName(), id, request.reason()));
    }

    @PostMapping("/{id}/hide")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> hide(Principal principal, @PathVariable String id) {
        return ApiResponse.success(service.hide(principal.getName(), id));
    }

    @PutMapping("/{id}/priority")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ListingResponse> priority(Principal principal, @PathVariable String id,
            @Valid @RequestBody ListingPriorityRequest request) {
        return ApiResponse.success(service.setPriority(principal.getName(), id, request.level()));
    }
}
