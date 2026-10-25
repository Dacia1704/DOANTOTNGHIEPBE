package com.tamdaptit.rentalmanagement.subscription.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServiceSubscriptionOrderRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.*;
import com.tamdaptit.rentalmanagement.subscription.service.ServiceSubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Đăng ký, gia hạn, đổi gói và quyền theo kỳ")
public class ServiceSubscriptionController {
    private final ServiceSubscriptionService service;

    @PostMapping("/orders")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ServiceSubscriptionOrderResponse> createOrder(
            Principal principal, @Valid @RequestBody ServiceSubscriptionOrderRequest request) {
        return ApiResponse.success(service.createOrder(principal.getName(), request));
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<CurrentSubscriptionResponse> current(Principal principal) {
        return ApiResponse.success(service.getCurrent(principal.getName()));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<ServiceSubscriptionOrderResponse>> orders(Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getOrders(principal.getName(), page, size));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<ServiceSubscriptionResponse>> history(Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getHistory(principal.getName(), page, size));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<ServiceSubscriptionOrderResponse> order(Principal principal, @PathVariable String id) {
        return ApiResponse.success(service.getOrder(principal.getName(), id));
    }

    @GetMapping("/periods")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<ServiceSubscriptionPeriodResponse>> periods(Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getPeriods(principal.getName(), page, size));
    }

    @GetMapping("/orders/awaiting-confirmation")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ServiceSubscriptionOrderResponse>> awaitingConfirmation(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getAwaitingConfirmation(page, size));
    }

    @PostMapping("/orders/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ServiceSubscriptionOrderResponse> confirm(Principal principal, @PathVariable String id) {
        return ApiResponse.success(service.confirmChange(principal.getName(), id));
    }
}
