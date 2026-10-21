package com.tamdaptit.rentalmanagement.service.impl;

import com.tamdaptit.rentalmanagement.dto.amenity.request.AmenityRequest;
import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.entity.Amenity;
import com.tamdaptit.rentalmanagement.exception.AppException;
import com.tamdaptit.rentalmanagement.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.repository.AmenityRepository;
import com.tamdaptit.rentalmanagement.service.AmenityService;
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
public class AmenityServiceImpl implements AmenityService {

    AmenityRepository amenityRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Amenity> getAll(int page, int size) {
        Page<Amenity> result = amenityRepository.findAll(PageRequest.of(
                Math.max(page, 0), Math.clamp(size, 1, 100),
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
    @Transactional(readOnly = true)
    public Amenity getById(String id) {
        return findAmenity(id);
    }

    @Override
    @Transactional
    public Amenity create(AmenityRequest request) {
        String name = request.getName().trim();
        if (amenityRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.AMENITY_ALREADY_EXISTS);
        }

        return amenityRepository.save(Amenity.builder()
                .name(name)
                .icon(normalizeIcon(request.getIcon()))
                .build());
    }

    @Override
    @Transactional
    public Amenity update(String id, AmenityRequest request) {
        Amenity amenity = findAmenity(id);
        String name = request.getName().trim();
        if (!amenity.getName().equalsIgnoreCase(name)
                && amenityRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.AMENITY_ALREADY_EXISTS);
        }

        amenity.setName(name);
        amenity.setIcon(normalizeIcon(request.getIcon()));
        return amenityRepository.save(amenity);
    }

    @Override
    @Transactional
    public void delete(String id) {
        amenityRepository.delete(findAmenity(id));
    }

    private Amenity findAmenity(String id) {
        return amenityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AMENITY_NOT_FOUND));
    }

    private String normalizeIcon(String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }
        return icon.trim();
    }
}
