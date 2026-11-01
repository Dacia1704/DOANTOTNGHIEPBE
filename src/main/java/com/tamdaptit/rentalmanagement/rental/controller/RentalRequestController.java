package com.tamdaptit.rentalmanagement.rental.controller;

import com.tamdaptit.rentalmanagement.common.dto.*;
import com.tamdaptit.rentalmanagement.rental.dto.request.*;
import com.tamdaptit.rentalmanagement.rental.dto.response.*;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus;
import com.tamdaptit.rentalmanagement.rental.service.RentalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class RentalRequestController {
    private final RentalService rentals;
    @PostMapping("/rental-requests") @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<ApiResponse<RentalRequestResponse>> create(Principal p,@Valid @RequestBody RentalRequestCreateRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.<RentalRequestResponse>builder().code(201)
            .message("Đã gửi yêu cầu thuê phòng").data(rentals.create(p.getName(),body)).build());
    }
    @GetMapping("/rental-requests/my") @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<PageResponse<RentalRequestResponse>> mine(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(rentals.requests(p.getName(),false,page,size,null,null,null));
    }
    @GetMapping("/landlord/rental-requests") @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<RentalRequestResponse>> landlord(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,
        @RequestParam(required=false) RentalRequestStatus status,@RequestParam(required=false) String propertyId,@RequestParam(required=false) String roomId) {
        return ApiResponse.success(rentals.requests(p.getName(),true,page,size,status,propertyId,roomId));
    }
    @GetMapping("/rental-requests/{id}") @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    public ApiResponse<RentalRequestResponse> get(Authentication p,@PathVariable String id) {
        return ApiResponse.success(rentals.request(p.getName(),id,isAdmin(p)));
    }
    @PostMapping("/rental-requests/{id}/cancel") @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<RentalRequestResponse> cancel(Principal p,@PathVariable String id) {
        return ApiResponse.success(rentals.cancelRequest(p.getName(),id,false,"Người thuê hủy yêu cầu"));
    }
    @PostMapping("/landlord/rental-requests/{id}/accept") @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<RentalAgreementResponse> accept(Principal p,@PathVariable String id) { return ApiResponse.success(rentals.accept(p.getName(),id)); }
    @PostMapping("/landlord/rental-requests/{id}/reject") @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<RentalRequestResponse> reject(Principal p,@PathVariable String id,@Valid @RequestBody(required=false) RentalRequestRejectRequest body) {
        return ApiResponse.success(rentals.cancelRequest(p.getName(),id,true,body==null?"Chủ trọ từ chối":body.reason()));
    }
    static boolean isAdmin(Authentication p) { return p.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")); }
}
