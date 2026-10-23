package com.tamdaptit.rentalmanagement.room.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.room.dto.request.AmenityRequest;
import com.tamdaptit.rentalmanagement.room.entity.Amenity;
import com.tamdaptit.rentalmanagement.room.service.AmenityService;
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
    @Operation(summary = "Lấy danh sách tiện ích")
    public ApiResponse<PageResponse<Amenity>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lấy danh sách tiện ích thành công", amenityService.getAll(page, size));
    }

    @GetMapping("/filter")
    @Operation(summary = "Lọc tiện ích theo tên")
    public ApiResponse<PageResponse<Amenity>> filter(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lọc tiện ích thành công", amenityService.filter(name, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy tiện ích theo ID")
    public ApiResponse<Amenity> getById(@PathVariable String id) {
        return ApiResponse.success("Lấy tiện ích thành công", amenityService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo tiện ích")
    public ApiResponse<Amenity> create(@RequestBody @Valid AmenityRequest request) {
        return ApiResponse.success("Tạo tiện ích thành công", amenityService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật tiện ích")
    public ApiResponse<Amenity> update(@PathVariable String id, @RequestBody @Valid AmenityRequest request) {
        return ApiResponse.success("Cập nhật tiện ích thành công", amenityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa tiện ích")
    public ApiResponse<Void> delete(@PathVariable String id) {
        amenityService.delete(id);
        return ApiResponse.success("Xóa tiện ích thành công", null);
    }
}
