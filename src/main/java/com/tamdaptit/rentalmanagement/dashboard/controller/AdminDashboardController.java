package com.tamdaptit.rentalmanagement.dashboard.controller;

import com.tamdaptit.rentalmanagement.dashboard.service.AdminDashboardService;
import com.tamdaptit.rentalmanagement.dashboard.dto.response.AdminDashboardResponse;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminDashboardResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(service.overview(from, to));
    }
}
