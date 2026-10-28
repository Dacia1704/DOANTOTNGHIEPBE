package com.tamdaptit.rentalmanagement.property.service.impl;

import com.tamdaptit.rentalmanagement.address.repository.*;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.property.dto.request.*;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyResponse;
import com.tamdaptit.rentalmanagement.property.entity.*;
import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;
import com.tamdaptit.rentalmanagement.property.mapper.PropertyMapper;
import com.tamdaptit.rentalmanagement.property.repository.*;
import com.tamdaptit.rentalmanagement.property.service.*;
import com.tamdaptit.rentalmanagement.room.entity.*;
import com.tamdaptit.rentalmanagement.room.mapper.RoomMapper;
import com.tamdaptit.rentalmanagement.room.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyServiceImpl implements PropertyService {
    private final PropertyRepository properties;
    private final PropertyTypeRepository propertyTypes;
    private final PropertyAmenityRepository propertyAmenities;
    private final PropertyImageRepository propertyImages;
    private final RoomRepository rooms;
    private final RoomTypeRepository roomTypes;
    private final RoomAmenityRepository roomAmenities;
    private final RoomImageRepository roomImages;
    private final AmenityRepository amenities;
    private final UserRepository users;
    private final ProvinceRepository provinces;
    private final WardRepository wards;
    private final PropertyMapper propertyMapper;
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public PropertyResponse create(String userId, CreatePropertyRequest request) {
        validateUnits(request);
        Property property = propertyMapper.toEntity(request);
        property.setName(request.name().trim());
        property.setAddressLine(request.addressLine().trim());
        property.setLandlord(users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));
        property.setPropertyType(propertyTypes.findById(request.propertyTypeId())
                .orElseThrow(() -> new AppException(ErrorCode.PROPERTY_TYPE_NOT_FOUND)));
        var province = provinces.findById(request.provinceId())
                .orElseThrow(() -> new AppException(ErrorCode.PROVINCE_NOT_FOUND));
        var ward = wards.findById(request.wardId()).orElseThrow(() -> new AppException(ErrorCode.WARD_NOT_FOUND));
        if (!ward.getProvince().getId().equals(province.getId()))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Phường không thuộc tỉnh/thành đã chọn");
        property.setProvince(province);
        property.setWard(ward);
        properties.save(property);
        findAmenities(request.amenityIds()).forEach(a -> propertyAmenities.save(
                PropertyAmenity.builder().property(property).amenity(a).build()));
        for (var floor : request.floors()) {
            for (var item : floor.rooms()) {
                var values = RoomDefaultsResolver.resolve(request.defaultRoom(), item);
                Room room = propertyMapper.toRoom(values);
                room.setProperty(property);
                room.setRoomNumber(item.roomNumber().trim());
                room.setFloorNumber(floor.floorNumber());
                room.setRoomType(roomTypes.findById(values.roomTypeId())
                        .orElseThrow(() -> new AppException(ErrorCode.ROOM_TYPE_NOT_FOUND)));
                rooms.save(room);
                findAmenities(item.amenityIds()).forEach(a -> roomAmenities.save(
                        RoomAmenity.builder().room(room).amenity(a).build()));
            }
        }
        // Flush all constraints inside the transaction before returning the response.
        properties.flush();
        return response(property);
    }

    private void validateUnits(CreatePropertyRequest request) {
        if (request.rentalMode() == null || request.floors() == null || request.floors().isEmpty())
            throw new AppException(ErrorCode.INVALID_REQUEST, "Cơ sở phải có đơn vị cho thuê và hình thức cho thuê");
        Set<String> numbers = new HashSet<>();
        int count = 0;
        for (var floor : request.floors()) {
            if (floor == null || floor.rooms() == null || floor.rooms().isEmpty()
                    || (floor.floorNumber() != null && floor.floorNumber() < 0))
                throw new AppException(ErrorCode.INVALID_REQUEST, "Tầng/phòng không hợp lệ");
            for (var room : floor.rooms()) {
                if (room == null || room.roomNumber() == null || room.roomNumber().isBlank()
                        || room.roomNumber().trim().length() > 30)
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Số phòng không hợp lệ");
                // MySQL's default collation compares room numbers case-insensitively.
                if (!numbers.add(room.roomNumber().trim().toLowerCase(Locale.ROOT)))
                    throw new AppException(ErrorCode.DUPLICATE_ROOM_NUMBER);
                RoomDefaultsResolver.resolve(request.defaultRoom(), room);
                count++;
            }
        }
        if (request.rentalMode() == PropertyRentalMode.WHOLE && count != 1)
            throw new AppException(ErrorCode.INVALID_REQUEST, "WHOLE phải có đúng một đơn vị cho thuê");
    }

    @Override
    public PropertyResponse get(String userId, String id) {
        Property property = properties.findById(id).orElseThrow(() -> new AppException(ErrorCode.PROPERTY_NOT_FOUND));
        if (!property.getLandlord().getId().equals(userId)) throw new AppException(ErrorCode.NOT_PROPERTY_OWNER);
        return response(property);
    }

    @Override
    public PageResponse<PropertyResponse> mine(String userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new AppException(ErrorCode.INVALID_REQUEST);
        var result = properties.findByLandlord_Id(userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.<PropertyResponse>builder().currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(this::response).toList()).build();
    }

    private List<Amenity> findAmenities(List<String> ids) {
        if (ids == null) return List.of();
        return ids.stream().distinct().map(id -> amenities.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AMENITY_NOT_FOUND))).toList();
    }

    private PropertyResponse response(Property property) {
        var units = rooms.findByProperty_IdOrderByRoomNumberAsc(property.getId());
        var ids = units.stream().map(Room::getId).toList();
        var amenitiesByRoom = ids.isEmpty() ? Map.<String, List<RoomAmenity>>of()
                : roomAmenities.findAllWithAmenityByRoomIds(ids).stream().collect(Collectors.groupingBy(a -> a.getRoom().getId()));
        var imagesByRoom = ids.isEmpty() ? Map.<String, List<RoomImage>>of()
                : roomImages.findAllByRoomIds(ids).stream().collect(Collectors.groupingBy(i -> i.getRoom().getId()));
        var responses = units.stream().map(r -> roomMapper.toResponse(r,
                amenitiesByRoom.getOrDefault(r.getId(), List.of()), imagesByRoom.getOrDefault(r.getId(), List.of()))).toList();
        return propertyMapper.toResponse(property, propertyAmenities.findAllByProperty_Id(property.getId()),
                propertyImages.findAllByProperty_IdOrderBySortOrderAsc(property.getId()), responses);
    }
}
