package com.tamdaptit.rentalmanagement.property.controller;

import com.tamdaptit.rentalmanagement.common.dto.*;
import com.tamdaptit.rentalmanagement.property.dto.request.CreatePropertyRequest;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyResponse;
import com.tamdaptit.rentalmanagement.property.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('LANDLORD')")
@Tag(name = "Properties", description = "Quản lý cơ sở và đơn vị cho thuê")
public class PropertyController {
    PropertyService properties;

    @PostMapping
    @Operation(summary = "Tạo cơ sở và toàn bộ phòng trong một giao dịch")
    public ApiResponse<PropertyResponse> create(Principal principal, @RequestBody @Valid CreatePropertyRequest request) {
        return ApiResponse.success("Tạo cơ sở cho thuê thành công", properties.create(principal.getName(), request));
    }

    @GetMapping("/mine")
    @Operation(summary = "Danh sách cơ sở của chủ trọ đang đăng nhập")
    public ApiResponse<PageResponse<PropertyResponse>> mine(Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lấy danh sách cơ sở thành công", properties.mine(principal.getName(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết cơ sở của chủ trọ")
    public ApiResponse<PropertyResponse> get(Principal principal, @PathVariable String id) {
        return ApiResponse.success("Lấy cơ sở thành công", properties.get(principal.getName(), id));
    }
}
