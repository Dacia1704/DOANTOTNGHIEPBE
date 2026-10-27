package com.tamdaptit.rentalmanagement.room.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.room.dto.request.AmenityRequest;
import com.tamdaptit.rentalmanagement.room.entity.Amenity;
import com.tamdaptit.rentalmanagement.room.repository.AmenityRepository;
import com.tamdaptit.rentalmanagement.room.repository.RoomAmenityRepository;
import com.tamdaptit.rentalmanagement.room.service.AmenityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AmenityServiceImpl implements AmenityService {

    private final AmenityRepository amenityRepository;
    private final com.tamdaptit.rentalmanagement.property.repository.PropertyAmenityRepository propertyAmenities;
    private final RoomAmenityRepository roomAmenityRepository;

    @Override
    public PageResponse<Amenity> getAll(int page, int size) {
        Page<Amenity> result = amenityRepository.findAll(PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.ASC, "name")));
        return PageResponse.<Amenity>builder()
                .currentPage(result.getNumber())
                .pageSize(result.getSize())
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .data(result.getContent())
                .build();
    }

    @Override
    public PageResponse<Amenity> filter(String name, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.ASC, "name"));
        Page<Amenity> result = name == null || name.isBlank()
                ? amenityRepository.findAll(pageable)
                : amenityRepository.findByNameContainingIgnoreCase(name.trim(), pageable);
        return PageResponse.<Amenity>builder().currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements()).data(result.getContent()).build();
    }

    @Override
    public Amenity getById(String id) {
        return findAmenity(id);
    }

    @Override
    @Transactional
    public Amenity create(AmenityRequest request) {
        String name = request.name().trim();
        if (amenityRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(
                    ErrorCode.AMENITY_ALREADY_EXISTS,
                    String.format("Tên tiện ích %s đã tồn tại", name));
        }
        return amenityRepository.save(Amenity.builder()
                .name(name)
                .icon(normalizeIcon(request.icon()))
                .build());
    }

    @Override
    @Transactional
    public Amenity update(String id, AmenityRequest request) {
        Amenity amenity = findAmenity(id);
        String name = request.name().trim();
        if (!amenity.getName().equalsIgnoreCase(name)
                && amenityRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(
                    ErrorCode.AMENITY_ALREADY_EXISTS,
                    String.format("Tên tiện ích %s đã tồn tại", name));
        }

        amenity.setName(name);
        amenity.setIcon(normalizeIcon(request.icon()));
        return amenityRepository.save(amenity);
    }

    /**
     * Chặn xoá tiện ích đang được gán cho phòng.
     *
     * <p>Không có bước kiểm tra này thì lỗi ràng buộc FK từ {@code room_amenities}
     * sẽ nổi lên dưới dạng 400 thô, không giải thích được cho client.
     */
    @Override
    @Transactional
    public void delete(String id) {
        Amenity amenity = findAmenity(id);
        if (roomAmenityRepository.existsByAmenity_Id(id) || propertyAmenities.existsByAmenity_Id(id)) {
            throw new AppException(
                    ErrorCode.AMENITY_IN_USE,
                    String.format(
                            "Tiện ích %s đang được gán cho phòng, vui lòng gỡ khỏi phòng trước khi xóa",
                            amenity.getName()));
        }
        amenityRepository.delete(amenity);
    }

    private Amenity findAmenity(String id) {
        return amenityRepository.findById(id)
                .orElseThrow(() -> new AppException(
                        ErrorCode.AMENITY_NOT_FOUND,
                        String.format("Không tìm thấy tiện ích với ID %s", id)));
    }

    private String normalizeIcon(String icon) {
        return icon == null || icon.isBlank() ? null : icon.trim();
    }
}
