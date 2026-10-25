package com.tamdaptit.rentalmanagement.payment.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.payment.dto.request.PaymentRequest;
import com.tamdaptit.rentalmanagement.payment.dto.response.*;
import com.tamdaptit.rentalmanagement.payment.service.PaymentService;
import com.tamdaptit.rentalmanagement.payment.service.PaymentQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService service;
    private final PaymentQueryService queries;

    @PostMapping("/{id}/check")
    @PreAuthorize("hasAnyRole('ADMIN','LANDLORD')")
    public ApiResponse<VnPayQueryResponse> check(Authentication auth, @PathVariable String id) {
        return ApiResponse.success(queries.check(auth.getName(), id, isAdmin(auth)));
    }

    @PostMapping("/vnpay/checkout")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PaymentCheckoutResponse> checkout(Authentication auth,
            @Valid @RequestBody PaymentRequest request, HttpServletRequest http) {
        return ApiResponse.success(service.checkout(auth.getName(), request, http.getRemoteAddr()));
    }

    @GetMapping("/vnpay/ipn")
    public VnPayIpnResponse ipn(@RequestParam MultiValueMap<String, String> parameters) {
        try {
            return service.processIpn(parameters);
        } catch (RuntimeException exception) {
            log.warn("VNPay IPN transaction could not complete: {}", exception.getClass().getSimpleName());
            return new VnPayIpnResponse("99", "Unknown error");
        }
    }

    @GetMapping("/vnpay/return")
    public ApiResponse<VnPayReturnResponse> returnResult(@RequestParam MultiValueMap<String, String> parameters) {
        return ApiResponse.success(service.verifyReturn(parameters));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LANDLORD')")
    public ApiResponse<PaymentResponse> get(Authentication auth, @PathVariable String id) {
        return ApiResponse.success(service.getById(auth.getName(), id, isAdmin(auth)));
    }

    @GetMapping("/{id}/invoice")
    @PreAuthorize("hasAnyRole('ADMIN','LANDLORD')")
    public ApiResponse<InvoiceResponse> invoice(Authentication auth, @PathVariable String id) {
        return ApiResponse.success(service.getInvoice(auth.getName(), id, isAdmin(auth)));
    }

    @GetMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<PaymentResponse>> history(Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getHistory(auth.getName(), page, size));
    }

    @GetMapping("/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<PaymentResponse>> review(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.getReviewQueue(page, size));
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
