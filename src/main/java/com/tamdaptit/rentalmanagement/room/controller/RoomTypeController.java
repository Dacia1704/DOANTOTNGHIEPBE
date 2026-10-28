package com.tamdaptit.rentalmanagement.room.controller;

import java.security.Principal;
import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.room.dto.request.RoomTypeRequest;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomTypeCategoryResponse;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomTypeResponse;
import com.tamdaptit.rentalmanagement.room.service.RoomTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/room-types")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Room types", description = "Quản lý loại phòng và danh mục phòng")
public class RoomTypeController {
    RoomTypeService roomTypeService;

    @GetMapping
    @Operation(summary = "Lấy danh sách loại phòng có phân trang")
    public ApiResponse<PageResponse<RoomTypeResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lấy danh sách loại phòng thành công", roomTypeService.getAll(page, size));
    }

    @GetMapping("/filter")
    @Operation(summary = "Lọc loại phòng theo tên và danh mục cha")
    public ApiResponse<PageResponse<RoomTypeResponse>> filter(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lọc loại phòng thành công", roomTypeService.filter(name, parentId, page, size));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasRole('LANDLORD')")
    @Operation(summary = "Lấy danh mục loại phòng kèm các phòng của chủ trọ")
    public ApiResponse<List<RoomTypeCategoryResponse>> getCategoriesWithRooms(Principal principal) {
        return ApiResponse.success("Lấy danh mục phòng thành công", roomTypeService.getCategoriesWithRooms(principal.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy loại phòng theo ID")
    public ApiResponse<RoomTypeResponse> getById(@PathVariable String id) {
        return ApiResponse.success("Lấy loại phòng thành công", roomTypeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo loại phòng")
    public ApiResponse<RoomTypeResponse> create(@RequestBody @Valid RoomTypeRequest request) {
        return ApiResponse.success("Tạo loại phòng thành công", roomTypeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật loại phòng")
    public ApiResponse<RoomTypeResponse> update(@PathVariable String id, @RequestBody @Valid RoomTypeRequest request) {
        return ApiResponse.success("Cập nhật loại phòng thành công", roomTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa loại phòng")
    public ApiResponse<Void> delete(@PathVariable String id) {
        roomTypeService.delete(id);
        return ApiResponse.success("Xóa loại phòng thành công", null);
    }
}
