package com.tamdaptit.rentalmanagement.room.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.room.dto.request.RoomTypeRequest;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomTypeCategoryResponse;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomTypeResponse;
import com.tamdaptit.rentalmanagement.room.entity.RoomType;
import com.tamdaptit.rentalmanagement.room.mapper.RoomTypeMapper;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import com.tamdaptit.rentalmanagement.room.repository.RoomTypeRepository;
import com.tamdaptit.rentalmanagement.room.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomTypeServiceImpl implements RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeMapper roomTypeMapper;

    @Override
    public PageResponse<RoomTypeResponse> getAll(int page, int size) {
        Page<RoomType> result = roomTypeRepository.findAll(pageRequest(page, size));
        return PageResponse.<RoomTypeResponse>builder()
                .currentPage(result.getNumber())
                .pageSize(result.getSize())
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(roomTypeMapper::toResponse).toList())
                .build();
    }

    @Override
    public PageResponse<RoomTypeResponse> filter(String name, String parentId, int page, int size) {
        var pageable = pageRequest(page, size);
        Page<RoomType> result;
        if (name != null && !name.isBlank() && parentId != null && !parentId.isBlank()) {
            result = roomTypeRepository.findByNameContainingIgnoreCaseAndParent_Id(name.trim(), parentId, pageable);
        } else if (name != null && !name.isBlank()) {
            result = roomTypeRepository.findByNameContainingIgnoreCase(name.trim(), pageable);
        } else if (parentId != null && !parentId.isBlank()) {
            result = roomTypeRepository.findByParent_Id(parentId, pageable);
        } else {
            result = roomTypeRepository.findAll(pageable);
        }
        return PageResponse.<RoomTypeResponse>builder().currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(roomTypeMapper::toResponse).toList()).build();
    }

    @Override
    public RoomTypeResponse getById(String id) {
        return roomTypeMapper.toResponse(findRoomType(id));
    }

    @Override
    @Transactional
    public RoomTypeResponse create(RoomTypeRequest request) {
        String name = request.name().trim();
        if (roomTypeRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(
                    ErrorCode.ROOM_TYPE_ALREADY_EXISTS,
                    String.format("Tên loại phòng %s đã tồn tại", name));
        }

        // Loai moi chua co cha nen khong co kha nang tao vong lap.
        RoomType parent = findParent(request.parentId());

        RoomType saved = roomTypeRepository.save(RoomType.builder()
                .name(name)
                .description(normalizeDescription(request.description()))
                .parent(parent)
                .build());
        return roomTypeMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public RoomTypeResponse update(String id, RoomTypeRequest request) {
        RoomType roomType = findRoomType(id);
        String name = request.name().trim();
        if (!roomType.getName().equalsIgnoreCase(name)
                && roomTypeRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(
                    ErrorCode.ROOM_TYPE_ALREADY_EXISTS,
                    String.format("Tên loại phòng %s đã tồn tại", name));
        }

        RoomType parent = findParent(request.parentId());
        if (parent != null) {
            ensureNoCycle(id, parent.getId());
        }

        roomType.setName(name);
        roomType.setDescription(normalizeDescription(request.description()));
        roomType.setParent(parent);
        return roomTypeMapper.toResponse(roomTypeRepository.save(roomType));
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
    public List<RoomTypeCategoryResponse> getCategoriesWithRooms() {
        var roomsByType = roomRepository.findAllForCategories().stream()
                .collect(java.util.stream.Collectors.groupingBy(room -> room.getRoomType().getId()));
        return roomTypeRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(type -> roomTypeMapper.toCategoryResponse(type,
                        roomsByType.getOrDefault(type.getId(), java.util.List.of())))
                .toList();
    }

    /**
     * Chan loai phong tro thanh cha cua chinh no.
     *
     * <p>FK {@code room_types.parent_id} chi chan vong lap truc tiep (A -&gt; A),
     * con vong lap gian tiep (A -&gt; B -&gt; C -&gt; A) van khong bi FK bat.
     * Vay vua phai duyet nguoc lenh chuoi cha de chan manh de hon.
     */
    private void ensureNoCycle(String typeId, String parentId) {
        Set<String> visited = new HashSet<>();
        String cursor = parentId;

        while (cursor != null) {
            if (!visited.add(cursor)) {
                throw new AppException(
                        ErrorCode.INVALID_ROOM_TYPE_PARENT,
                        "Loại phòng cha tạo vòng lặp không hợp lệ");
            }
            if (cursor.equals(typeId)) {
                throw new AppException(
                        ErrorCode.INVALID_ROOM_TYPE_PARENT,
                        "Không thể đặt loại phòng làm cha của chính nó hoặc của hậu duệ của nó");
            }

            RoomType ancestor = roomTypeRepository.findById(cursor).orElse(null);
            cursor = ancestor == null || ancestor.getParent() == null
                    ? null
                    : ancestor.getParent().getId();
        }
    }

    private RoomType findRoomType(String id) {
        return roomTypeRepository.findById(id)
                .orElseThrow(() -> new AppException(
                        ErrorCode.ROOM_TYPE_NOT_FOUND,
                        String.format("Không tìm thấy loại phòng với ID %s", id)));
    }

    private RoomType findParent(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        return findRoomType(parentId.trim());
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.ASC, "name"));
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }
}
