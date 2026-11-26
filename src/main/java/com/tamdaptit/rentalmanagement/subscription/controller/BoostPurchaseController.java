package com.tamdaptit.rentalmanagement.subscription.controller;

import java.security.Principal;
import java.util.List;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostAllocationRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostPurchaseRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ActiveBoostResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostAllocationResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostPurchaseResponse;
import com.tamdaptit.rentalmanagement.subscription.service.BoostAllocationService;
import com.tamdaptit.rentalmanagement.subscription.service.BoostPurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boost-subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LANDLORD')")
public class BoostPurchaseController {
    private final BoostPurchaseService service;
    private final BoostAllocationService allocations;

    @GetMapping("/active")
    public ApiResponse<List<ActiveBoostResponse>> active(Principal principal) {
        return ApiResponse.success(allocations.getActive(principal.getName()));
    }

    @PutMapping("/allocations/{listingId}")
    public ApiResponse<BoostAllocationResponse> assign(
        Principal principal,
        @PathVariable String listingId,
        @Valid @RequestBody BoostAllocationRequest request
    ) {
        return ApiResponse.success(allocations.assign(principal.getName(), listingId, request));
    }

    @DeleteMapping("/allocations/{listingId}")
    public ApiResponse<Void> unassign(Principal principal, @PathVariable String listingId) {
        allocations.unassign(principal.getName(), listingId);
        return ApiResponse.success((Void) null);
    }

    @PostMapping
    public ApiResponse<BoostPurchaseResponse> create(
        Principal principal,
        @Valid @RequestBody BoostPurchaseRequest request
    ) {
        return ApiResponse.success(service.create(principal.getName(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<BoostPurchaseResponse>> history(
        Principal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(service.getHistory(principal.getName(), page, size));
    }
}
