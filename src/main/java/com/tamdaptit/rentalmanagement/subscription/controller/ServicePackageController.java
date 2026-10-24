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
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServicePackagePriceRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServicePackageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServicePackagePriceResponse;
import com.tamdaptit.rentalmanagement.subscription.service.ServicePackageService;
import java.util.List;

@RestController
@RequestMapping("/api/service-packages")
@RequiredArgsConstructor
@Tag(name = "Service packages", description = "Danh mục gói dịch vụ và giá theo thời hạn")
public class ServicePackageController {
    private final ServicePackageService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('LANDLORD') and !#includeInactive)")
    @Operation(summary = "Danh sách gói; chỉ admin được xem cả gói chưa bán")
    public ApiResponse<PageResponse<ServicePackageResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(service.getAll(page, size, includeInactive));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('LANDLORD') and !#includeInactive)")
    @Operation(summary = "Chi tiết gói dịch vụ")
    public ApiResponse<ServicePackageResponse> getById(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(service.getById(id, includeInactive));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin tạo gói dịch vụ")
    public ApiResponse<ServicePackageResponse> create(@RequestBody @Valid ServicePackageRequest request) {
        return ApiResponse.success("Tạo gói dịch vụ thành công", service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin cập nhật quyền lợi; giữ nguyên snapshot đã mua")
    public ApiResponse<ServicePackageResponse> update(
            @PathVariable String id, @RequestBody @Valid ServicePackageRequest request) {
        return ApiResponse.success("Cập nhật gói dịch vụ thành công", service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin bật hoặc ngừng bán gói")
    public ApiResponse<ServicePackageResponse> setActive(
            @PathVariable String id, @RequestBody @Valid CatalogStatusRequest request) {
        return ApiResponse.success("Cập nhật trạng thái gói thành công", service.setActive(id, request.active()));
    }

    @GetMapping("/{packageId}/prices")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('LANDLORD') and !#includeInactive)")
    @Operation(summary = "Các mức giá theo thời hạn của gói")
    public ApiResponse<List<ServicePackagePriceResponse>> getPrices(
            @PathVariable String packageId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(service.getPrices(packageId, includeInactive));
    }

    @PostMapping("/{packageId}/prices")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin thêm giá cho 1, 3, 6 hoặc 12 tháng")
    public ApiResponse<ServicePackagePriceResponse> createPrice(
            @PathVariable String packageId, @RequestBody @Valid ServicePackagePriceRequest request) {
        return ApiResponse.success("Thêm giá gói thành công", service.createPrice(packageId, request));
    }

    @PutMapping("/{packageId}/prices/{priceId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin cập nhật mức giá của đúng gói")
    public ApiResponse<ServicePackagePriceResponse> updatePrice(
            @PathVariable String packageId, @PathVariable String priceId,
            @RequestBody @Valid ServicePackagePriceRequest request) {
        return ApiResponse.success("Cập nhật giá gói thành công", service.updatePrice(packageId, priceId, request));
    }

    @PatchMapping("/{packageId}/prices/{priceId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin bật hoặc ngừng bán một thời hạn")
    public ApiResponse<ServicePackagePriceResponse> setPriceActive(
            @PathVariable String packageId, @PathVariable String priceId,
            @RequestBody @Valid CatalogStatusRequest request) {
        return ApiResponse.success("Cập nhật trạng thái giá thành công",
                service.setPriceActive(packageId, priceId, request.active()));
    }
}
