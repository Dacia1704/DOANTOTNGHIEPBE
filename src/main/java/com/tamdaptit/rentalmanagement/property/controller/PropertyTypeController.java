package com.tamdaptit.rentalmanagement.property.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.property.dto.request.PropertyTypeRequest;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyTypeResponse;
import com.tamdaptit.rentalmanagement.property.service.PropertyTypeService;
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
@RequestMapping("/api/property-types")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Property types", description = "Quản lý danh mục loại bất động sản")
public class PropertyTypeController {
    PropertyTypeService propertyTypeService;

    @GetMapping
    @Operation(summary = "Lấy danh sách loại bất động sản có phân trang")
    public ApiResponse<PageResponse<PropertyTypeResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lấy danh sách loại bất động sản thành công", propertyTypeService.getAll(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy loại bất động sản theo ID")
    public ApiResponse<PropertyTypeResponse> getById(@PathVariable String id) {
        return ApiResponse.success("Lấy loại bất động sản thành công", propertyTypeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo loại bất động sản")
    public ApiResponse<PropertyTypeResponse> create(@RequestBody @Valid PropertyTypeRequest request) {
        return ApiResponse.success("Tạo loại bất động sản thành công", propertyTypeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật loại bất động sản")
    public ApiResponse<PropertyTypeResponse> update(@PathVariable String id, @RequestBody @Valid PropertyTypeRequest request) {
        return ApiResponse.success("Cập nhật loại bất động sản thành công", propertyTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa loại bất động sản")
    public ApiResponse<Void> delete(@PathVariable String id) {
        propertyTypeService.delete(id);
        return ApiResponse.success("Xóa loại bất động sản thành công", null);
    }
}
