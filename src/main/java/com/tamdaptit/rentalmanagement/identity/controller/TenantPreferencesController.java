package com.tamdaptit.rentalmanagement.identity.controller;

import java.security.Principal;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.identity.dto.request.TenantPreferences;
import com.tamdaptit.rentalmanagement.identity.service.TenantPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/tenant-profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT')")
public class TenantPreferencesController {
    private final TenantPreferencesService service;

    @GetMapping
    public ApiResponse<TenantPreferences> get(Principal principal) {
        return ApiResponse.success(service.get(principal.getName()));
    }

    @PutMapping
    public ApiResponse<TenantPreferences> save(Principal principal, @RequestBody @Valid TenantPreferences request) {
        return ApiResponse.success(service.save(principal.getName(), request));
    }
}
