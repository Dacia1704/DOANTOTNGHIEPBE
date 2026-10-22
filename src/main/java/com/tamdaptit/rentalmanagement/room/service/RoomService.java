package com.tamdaptit.rentalmanagement.room.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.room.dto.request.RoomRequest;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface RoomService {
    PageResponse<RoomResponse> search(int page, int size, String typeId, String provinceId, String wardId,
            RoomRentalStatus status, BigDecimal minPrice, BigDecimal maxPrice);
    PageResponse<RoomResponse> mine(String userId, int page, int size);
    RoomResponse get(String id);
    RoomResponse create(String userId, RoomRequest request, List<MultipartFile> files);
    RoomResponse update(String userId, String id, RoomRequest request);
    void delete(String userId, String id);
    List<RoomResponse.RoomImageResponse> uploadImages(String userId, String id, List<MultipartFile> files);
    void deleteImage(String userId, String id, String imageId);
}
