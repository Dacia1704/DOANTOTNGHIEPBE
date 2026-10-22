package com.tamdaptit.rentalmanagement.room.service.impl;

import com.tamdaptit.rentalmanagement.address.repository.ProvinceRepository;
import com.tamdaptit.rentalmanagement.address.repository.WardRepository;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.room.dto.request.RoomRequest;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import com.tamdaptit.rentalmanagement.room.entity.*;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import com.tamdaptit.rentalmanagement.room.repository.*;
import com.tamdaptit.rentalmanagement.room.mapper.RoomMapper;
import com.tamdaptit.rentalmanagement.room.service.RoomService;
import com.tamdaptit.rentalmanagement.storage.service.ImageStorageService;
import com.tamdaptit.rentalmanagement.storage.service.StorageFolder;

import java.math.BigDecimal;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomServiceImpl implements RoomService {
    private final RoomRepository rooms;
    private final RoomMapper roomMapper;
    private final RoomTypeRepository types;
    private final AmenityRepository amenities;
    private final RoomAmenityRepository roomAmenities;
    private final RoomImageRepository images;
    private final UserRepository users;
    private final ProvinceRepository provinces;
    private final WardRepository wards;
    private final ImageStorageService storage;

    @Override
    public PageResponse<RoomResponse> search(int page, int size, String typeId, String provinceId, String wardId,
                                             RoomRentalStatus status, BigDecimal minPrice, BigDecimal maxPrice) {
        validatePage(page, size);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new AppException(ErrorCode.INVALID_REQUEST, "minPrice không được lớn hơn maxPrice");
        var result = rooms.search(null, typeId, provinceId, wardId, status, minPrice, maxPrice,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return page(result);
    }

    @Override
    public PageResponse<RoomResponse> mine(String userId, int page, int size) {
        validatePage(page, size);
        return page(rooms.search(userId, null, null, null, null, null, null,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @Override
    public RoomResponse get(String id) {
        return responses(List.of(find(id))).get(0);
    }

    @Override
    @Transactional
    public RoomResponse create(String userId, RoomRequest request, List<MultipartFile> files) {
        Room room = new Room();
        room.setLandlord(users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));
        apply(room, request);
        rooms.saveAndFlush(room);
        setAmenities(room, request.amenityIds());
        if (files != null && !files.isEmpty()) {
            if (files.size() > 10) throw new AppException(ErrorCode.IMAGE_TOO_MANY);
            var stored = storage.uploadAll(StorageFolder.forRoom(room.getId()), files);
            var order = new java.util.concurrent.atomic.AtomicInteger(0);
            stored.forEach(file -> images.save(RoomImage.builder().room(room).url(file.url())
                    .publicId(file.publicId()).sortOrder(order.getAndIncrement()).build()));
        }
        return responses(List.of(room)).get(0);
    }

    @Override
    @Transactional
    public RoomResponse update(String userId, String id, RoomRequest request) {
        Room room = owned(userId, id);
        apply(room, request);
        setAmenities(room, request.amenityIds());
        return responses(List.of(room)).get(0);
    }

    @Override
    @Transactional
    public void delete(String userId, String id) {
        Room room = owned(userId, id);
        rooms.delete(room);
        storage.deleteByPrefix(StorageFolder.forRoom(id));
    }

    @Override
    @Transactional
    public List<RoomResponse.RoomImageResponse> uploadImages(String userId, String id, List<MultipartFile> files) {
        Room room = owned(userId, id);
        if (files == null || files.isEmpty() || files.size() > 10)
            throw new AppException(ErrorCode.IMAGE_TOO_MANY);
        var stored = storage.uploadAll(StorageFolder.forRoom(id), files);
        var order = new java.util.concurrent.atomic.AtomicInteger(images.findAllByRoom_IdOrderBySortOrderAsc(id).size());
        return stored.stream().map(file -> images.save(RoomImage.builder().room(room).url(file.url())
                .publicId(file.publicId()).sortOrder(order.getAndIncrement()).build()))
                .map(roomMapper::toImageResponse).toList();
    }

    @Override
    @Transactional
    public void deleteImage(String userId, String id, String imageId) {
        owned(userId, id);
        RoomImage image = images.findById(imageId).filter(i -> i.getRoom().getId().equals(id))
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_IMAGE_NOT_FOUND));
        images.delete(image);
        if (image.getPublicId() != null) storage.delete(image.getPublicId());
    }

    private Room find(String id) {
        return rooms.findDetailedById(id).orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
    }

    private Room owned(String userId, String id) {
        Room room = find(id);
        if (!room.getLandlord().getId().equals(userId)) throw new AppException(ErrorCode.NOT_ROOM_OWNER);
        return room;
    }

    private void apply(Room room, RoomRequest request) {
        roomMapper.updateFromRequest(request, room);
        room.setRoomType(types.findById(request.roomTypeId()).orElseThrow(() -> new AppException(ErrorCode.ROOM_TYPE_NOT_FOUND)));
        var province = provinces.findById(request.provinceId()).orElseThrow(() -> new AppException(ErrorCode.PROVINCE_NOT_FOUND));
        var ward = wards.findById(request.wardId()).orElseThrow(() -> new AppException(ErrorCode.WARD_NOT_FOUND));
        if (!ward.getProvince().getId().equals(province.getId()))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Phường không thuộc tỉnh/thành đã chọn");
        room.setProvince(province);
        room.setWard(ward);
        room.setAddressLine(request.addressLine().trim());
    }

    private void setAmenities(Room room, List<String> ids) {
        roomAmenities.deleteAll(roomAmenities.findAllByRoom_Id(room.getId()));
        if (ids == null) return;
        ids.stream().distinct().map(id -> amenities.findById(id).orElseThrow(() -> new AppException(ErrorCode.AMENITY_NOT_FOUND)))
                .forEach(amenity -> roomAmenities.save(RoomAmenity.builder().room(room).amenity(amenity).build()));
    }

    private PageResponse<RoomResponse> page(org.springframework.data.domain.Page<Room> p) {
        return PageResponse.<RoomResponse>builder().currentPage(p.getNumber()).pageSize(p.getSize()).totalPages(p.getTotalPages())
                .totalElements(p.getTotalElements()).data(responses(p.getContent())).build();
    }

    private List<RoomResponse> responses(List<Room> roomList) {
        if (roomList.isEmpty()) return List.of();
        var ids = roomList.stream().map(Room::getId).toList();
        var amenitiesByRoom = roomAmenities.findAllWithAmenityByRoomIds(ids).stream().collect(
                java.util.stream.Collectors.groupingBy(a -> a.getRoom().getId()));
        var imagesByRoom = images.findAllByRoomIds(ids).stream().collect(
                java.util.stream.Collectors.groupingBy(i -> i.getRoom().getId()));
        return roomList.stream().map(room -> roomMapper.toResponse(room,
                amenitiesByRoom.getOrDefault(room.getId(), List.of()),
                imagesByRoom.getOrDefault(room.getId(), List.of()))).toList();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new AppException(ErrorCode.INVALID_REQUEST, "page phải >= 0 và size từ 1 đến 100");
    }
}
