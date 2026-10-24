package com.tamdaptit.rentalmanagement.subscription.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostPurchaseRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostPurchaseResponse;
import com.tamdaptit.rentalmanagement.subscription.service.BoostPurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/boost-subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LANDLORD')")
public class BoostPurchaseController {
    private final BoostPurchaseService service;

    @PostMapping
    public ApiResponse<BoostPurchaseResponse> create(Principal principal, @Valid @RequestBody BoostPurchaseRequest request) {
        return ApiResponse.success(service.create(principal.getName(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<BoostPurchaseResponse>> history(Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getHistory(principal.getName(), page, size));
    }
}
