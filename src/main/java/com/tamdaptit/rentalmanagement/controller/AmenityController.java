package com.tamdaptit.rentalmanagement.controller;

import com.tamdaptit.rentalmanagement.common.ApiResponse;
import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.dto.amenity.request.AmenityRequest;
import com.tamdaptit.rentalmanagement.entity.Amenity;
import com.tamdaptit.rentalmanagement.service.AmenityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/amenities")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Amenities", description = "Quản lý tiện ích phòng")
public class AmenityController {

    AmenityService amenityService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách tiện ích")
    public ApiResponse<PageResponse<Amenity>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lấy danh sách tiện ích thành công", amenityService.getAll(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy tiện ích theo ID")
    public ApiResponse<Amenity> getById(@PathVariable String id) {
        return ApiResponse.success("Lấy tiện ích thành công", amenityService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AMENITY_CREATE') or hasRole('ADMIN')")
    @Operation(summary = "Tạo tiện ích", description = "Yêu cầu quyền AMENITY_WRITE hoặc vai trò ADMIN")
    public ApiResponse<Amenity> create(@RequestBody @Valid AmenityRequest request) {
        return ApiResponse.success("Tạo tiện ích thành công", amenityService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AMENITY_CREATE') or hasRole('ADMIN')")
    @Operation(summary = "Cập nhật tiện ích", description = "Yêu cầu quyền AMENITY_WRITE hoặc vai trò ADMIN")
    public ApiResponse<Amenity> update(
            @PathVariable String id,
            @RequestBody @Valid AmenityRequest request) {
        return ApiResponse.success("Cập nhật tiện ích thành công", amenityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('AMENITY_CREATE') or hasRole('ADMIN')")
    @Operation(summary = "Xóa tiện ích", description = "Yêu cầu quyền AMENITY_WRITE hoặc vai trò ADMIN")
    public ApiResponse<Void> delete(@PathVariable String id) {
        amenityService.delete(id);
        return ApiResponse.success("Xóa tiện ích thành công", null);
    }
}
