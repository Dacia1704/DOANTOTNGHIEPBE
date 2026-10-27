package com.tamdaptit.rentalmanagement.room.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.room.dto.request.RoomRequest;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import com.tamdaptit.rentalmanagement.room.service.RoomService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService rooms;

    @GetMapping("/filter")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<RoomResponse>> filter(Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String roomTypeName,
            @RequestParam(required = false) String provinceId,
            @RequestParam(required = false) String wardId,
            @RequestParam(required = false) RoomRentalStatus rentalStatus,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> amenityNames) {
        return ApiResponse.success("Lọc phòng thành công",
                rooms.searchMine(principal.getName(), page, size, roomTypeName, provinceId, wardId, rentalStatus, minPrice, maxPrice, amenityNames));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<RoomResponse>> mine(Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Lấy danh sách phòng của tôi thành công", rooms.mine(principal.getName(), page, size));
    }

    @GetMapping("/mine/filter")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<RoomResponse>> filterMine(Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String roomTypeName,
            @RequestParam(required = false) String provinceId,
            @RequestParam(required = false) String wardId,
            @RequestParam(required = false) RoomRentalStatus rentalStatus,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> amenityNames) {
        return ApiResponse.success("Lọc phòng của tôi thành công",
                rooms.searchMine(principal.getName(), page, size, roomTypeName, provinceId, wardId, rentalStatus,
                        minPrice, maxPrice, amenityNames));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<RoomResponse> get(Principal principal, @PathVariable String id) {
        return ApiResponse.success("Lấy thông tin phòng thành công", rooms.get(principal.getName(), id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<RoomResponse> create(Principal principal,
            @RequestPart("room") @Valid RoomRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ApiResponse.success("Thêm phòng thành công", rooms.create(principal.getName(), request, files));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<RoomResponse> update(Principal principal, @PathVariable String id,
            @RequestBody @Valid RoomRequest request) {
        return ApiResponse.success("Cập nhật phòng thành công", rooms.update(principal.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<Void> delete(Principal principal, @PathVariable String id) {
        rooms.delete(principal.getName(), id);
        return ApiResponse.success("Xóa phòng thành công", null);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<List<RoomResponse.RoomImageResponse>> uploadImages(Principal principal, @PathVariable String id,
            @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.success("Tải ảnh phòng thành công", rooms.uploadImages(principal.getName(), id, files));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<Void> deleteImage(Principal principal, @PathVariable String id, @PathVariable String imageId) {
        rooms.deleteImage(principal.getName(), id, imageId);
        return ApiResponse.success("Xóa ảnh phòng thành công", null);
    }
}
