package com.tamdaptit.rentalmanagement.service.impl;

import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.dto.roomtype.request.RoomTypeRequest;
import com.tamdaptit.rentalmanagement.dto.roomtype.response.RoomSummaryResponse;
import com.tamdaptit.rentalmanagement.dto.roomtype.response.RoomTypeCategoryResponse;
import com.tamdaptit.rentalmanagement.dto.roomtype.response.RoomTypeResponse;
import com.tamdaptit.rentalmanagement.entity.Room;
import com.tamdaptit.rentalmanagement.entity.RoomType;
import com.tamdaptit.rentalmanagement.exception.AppException;
import com.tamdaptit.rentalmanagement.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.repository.RoomRepository;
import com.tamdaptit.rentalmanagement.repository.RoomTypeRepository;
import com.tamdaptit.rentalmanagement.service.RoomTypeService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomTypeServiceImpl implements RoomTypeService {

    RoomTypeRepository roomTypeRepository;
    RoomRepository roomRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoomTypeResponse> getAll(int page, int size) {
        Page<RoomType> result = roomTypeRepository.findAll(PageRequest.of(
                Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.ASC, "name")));
        return PageResponse.<RoomTypeResponse>builder()
                .currentPage(result.getNumber())
                .pageSize(result.getSize())
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(this::toResponse).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RoomTypeResponse getById(String id) {
        return toResponse(findRoomType(id));
    }

    @Override
    @Transactional
    public RoomTypeResponse create(RoomTypeRequest request) {
        String name = request.getName().trim();
        if (roomTypeRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.ROOM_TYPE_ALREADY_EXISTS);
        }
        RoomType savedRoomType = roomTypeRepository.save(RoomType.builder()
                .name(name)
                .description(normalizeDescription(request.getDescription()))
                .parent(findParent(request.getParentId()))
                .build());
        return toResponse(savedRoomType);
    }

    @Override
    @Transactional
    public RoomTypeResponse update(String id, RoomTypeRequest request) {
        RoomType roomType = findRoomType(id);
        String name = request.getName().trim();
        if (!roomType.getName().equalsIgnoreCase(name)
                && roomTypeRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.ROOM_TYPE_ALREADY_EXISTS);
        }
        RoomType parent = findParent(request.getParentId());
        if (parent != null && parent.getId().equals(id)) {
            throw new AppException(ErrorCode.INVALID_ROOM_TYPE_PARENT);
        }

        roomType.setName(name);
        roomType.setDescription(normalizeDescription(request.getDescription()));
        roomType.setParent(parent);
        return toResponse(roomTypeRepository.save(roomType));
    }

    @Override
    @Transactional
    public void delete(String id) {
        RoomType roomType = findRoomType(id);
        if (roomTypeRepository.existsByParent_Id(id)) {
            throw new AppException(ErrorCode.ROOM_TYPE_HAS_CHILDREN);
        }
        if (roomRepository.existsByRoomType_Id(id)) {
            throw new AppException(ErrorCode.ROOM_TYPE_IN_USE);
        }
        roomTypeRepository.delete(roomType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomTypeCategoryResponse> getCategoriesWithRooms() {
        return roomTypeRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(type -> RoomTypeCategoryResponse.builder()
                        .id(type.getId())
                        .name(type.getName())
                        .description(type.getDescription())
                        .parentId(type.getParent() == null ? null : type.getParent().getId())
                        .rooms(roomRepository.findAllForRoomType(type.getId()).stream()
                                .map(this::toRoomSummary)
                                .toList())
                        .build())
                .toList();
    }

    private RoomType findRoomType(String id) {
        return roomTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_TYPE_NOT_FOUND));
    }

    private RoomType findParent(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        return findRoomType(parentId.trim());
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private RoomTypeResponse toResponse(RoomType roomType) {
        RoomType parent = roomType.getParent();
        return RoomTypeResponse.builder()
                .id(roomType.getId())
                .name(roomType.getName())
                .description(roomType.getDescription())
                .parentId(parent == null ? null : parent.getId())
                .parentName(parent == null ? null : parent.getName())
                .createdAt(roomType.getCreatedAt())
                .updatedAt(roomType.getUpdatedAt())
                .build();
    }

    private RoomSummaryResponse toRoomSummary(Room room) {
        return RoomSummaryResponse.builder()
                .id(room.getId())
                .price(room.getPrice())
                .areaM2(room.getAreaM2())
                .maxOccupants(room.getMaxOccupants())
                .addressLine(room.getAddressLine())
                .provinceName(room.getProvince().getName())
                .wardName(room.getWard().getName())
                .rentalStatus(room.getRentalStatus())
                .build();
    }
}
