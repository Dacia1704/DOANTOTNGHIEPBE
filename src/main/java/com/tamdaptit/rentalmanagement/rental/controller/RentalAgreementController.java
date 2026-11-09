package com.tamdaptit.rentalmanagement.rental.controller;

import com.tamdaptit.rentalmanagement.common.dto.*;
import com.tamdaptit.rentalmanagement.rental.dto.request.*;
import com.tamdaptit.rentalmanagement.rental.dto.response.*;
import com.tamdaptit.rentalmanagement.rental.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class RentalAgreementController {
    private final RentalService rentals;
    private final RentalContractService contracts;
    @GetMapping("/rental-agreements/my") @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<PageResponse<RentalAgreementResponse>> mine(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(rentals.agreements(p.getName(),false,page,size));
    }
    @GetMapping("/landlord/rental-agreements") @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<RentalAgreementResponse>> landlord(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(rentals.agreements(p.getName(),true,page,size));
    }
    @GetMapping("/rental-agreements/{id}") @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    public ApiResponse<RentalAgreementResponse> get(Authentication p,@PathVariable String id) {
        return ApiResponse.success(rentals.agreement(p.getName(),id,RentalRequestController.isAdmin(p)));
    }
    @GetMapping("/rental-agreements/{id}/pdf") @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    public ResponseEntity<byte[]> pdf(Authentication p,@PathVariable String id,@RequestParam(defaultValue="false") boolean download) {
        var content=contracts.download(p.getName(),id,RentalRequestController.isAdmin(p));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,(download?"attachment":"inline")+"; filename=\""+content.code()+".pdf\"")
            .body(content.bytes());
    }
    @PostMapping("/rental-agreements/{id}/confirm") @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<RentalAgreementResponse> confirm(Principal p,@PathVariable String id,@Valid @RequestBody RentalAgreementConfirmRequest body) {
        return ApiResponse.success(rentals.confirm(p.getName(),id,body));
    }
    @PostMapping("/rental-agreements/{id}/decline") @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<RentalAgreementResponse> decline(Principal p,@PathVariable String id) { return ApiResponse.success(rentals.decline(p.getName(),id)); }
    @PostMapping("/landlord/rental-agreements/{id}/checkout") @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<RentalAgreementResponse> checkout(Principal p,@PathVariable String id,@Valid @RequestBody RentalCheckoutRequest body) {
        return ApiResponse.success(rentals.checkout(p.getName(),id,body));
    }
}
