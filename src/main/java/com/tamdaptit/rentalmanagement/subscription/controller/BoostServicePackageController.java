package com.tamdaptit.rentalmanagement.subscription.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.request.CatalogStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostServicePackageResponse;
import com.tamdaptit.rentalmanagement.subscription.service.BoostServicePackageService;

@RestController
@RequestMapping("/api/boost-packages")
@RequiredArgsConstructor
@Tag(name = "Boost packages", description = "Danh mục Boost ưu tiên cao mua riêng")
public class BoostServicePackageController {
    private final BoostServicePackageService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('LANDLORD') and !#includeInactive)")
    @Operation(summary = "Danh sách Boost đang bán; admin có thể xem cả gói ngừng bán")
    public ApiResponse<PageResponse<BoostServicePackageResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(service.getAll(page, size, includeInactive));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('LANDLORD') and !#includeInactive)")
    @Operation(summary = "Chi tiết gói Boost")
    public ApiResponse<BoostServicePackageResponse> getById(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(service.getById(id, includeInactive));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin tạo Boost cao 1, 3 hoặc 6 tháng")
    public ApiResponse<BoostServicePackageResponse> create(@RequestBody @Valid BoostServicePackageRequest request) {
        return ApiResponse.success("Tạo gói Boost thành công", service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin cập nhật danh mục Boost")
    public ApiResponse<BoostServicePackageResponse> update(
            @PathVariable String id, @RequestBody @Valid BoostServicePackageRequest request) {
        return ApiResponse.success("Cập nhật gói Boost thành công", service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin bật hoặc ngừng bán Boost")
    public ApiResponse<BoostServicePackageResponse> setActive(
            @PathVariable String id, @RequestBody @Valid CatalogStatusRequest request) {
        return ApiResponse.success("Cập nhật trạng thái Boost thành công", service.setActive(id, request.active()));
    }
}
