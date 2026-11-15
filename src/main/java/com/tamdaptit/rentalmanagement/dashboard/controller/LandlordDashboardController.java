package com.tamdaptit.rentalmanagement.dashboard.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.dashboard.dto.response.LandlordDashboardResponse;
import com.tamdaptit.rentalmanagement.dashboard.service.LandlordDashboardService;
import java.security.Principal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/landlord/dashboard")
@RequiredArgsConstructor
public class LandlordDashboardController {
    private final LandlordDashboardService service;

    @GetMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<LandlordDashboardResponse> overview(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(service.overview(principal.getName(), from, to));
    }
}
