package com.tamdaptit.rentalmanagement.property.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.property.dto.request.PropertyTypeRequest;
import com.tamdaptit.rentalmanagement.property.dto.response.PropertyTypeResponse;
import com.tamdaptit.rentalmanagement.property.entity.PropertyType;
import com.tamdaptit.rentalmanagement.property.mapper.PropertyTypeMapper;
import com.tamdaptit.rentalmanagement.property.repository.*;
import com.tamdaptit.rentalmanagement.property.service.PropertyTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyTypeServiceImpl implements PropertyTypeService {
    private final PropertyTypeRepository propertyTypes;
    private final PropertyRepository properties;
    private final PropertyTypeMapper mapper;

    @Override
    public PageResponse<PropertyTypeResponse> getAll(int page, int size) {
        var result = propertyTypes.findAll(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name")));
        return PageResponse.<PropertyTypeResponse>builder().currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(mapper::toResponse).toList()).build();
    }

    @Override
    public PropertyTypeResponse getById(String id) { return mapper.toResponse(find(id)); }

    @Override
    @Transactional
    public PropertyTypeResponse create(PropertyTypeRequest request) {
        var type = mapper.toEntity(request);
        apply(type, request);
        return mapper.toResponse(propertyTypes.save(type));
    }

    @Override
    @Transactional
    public PropertyTypeResponse update(String id, PropertyTypeRequest request) {
        var type = find(id);
        apply(type, request);
        return mapper.toResponse(propertyTypes.save(type));
    }

    private void apply(PropertyType type, PropertyTypeRequest request) {
        String name = request.name().trim();
        if ((type.getId() == null || !type.getName().equalsIgnoreCase(name)) && propertyTypes.existsByNameIgnoreCase(name))
            throw new AppException(ErrorCode.PROPERTY_TYPE_ALREADY_EXISTS);
        mapper.update(request, type);
        type.setName(name);
        type.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
    }

    @Override
    @Transactional
    public void delete(String id) {
        var type = find(id);
        if (properties.existsByPropertyType_Id(id)) throw new AppException(ErrorCode.PROPERTY_TYPE_IN_USE);
        propertyTypes.delete(type);
    }

    private PropertyType find(String id) {
        return propertyTypes.findById(id).orElseThrow(() -> new AppException(ErrorCode.PROPERTY_TYPE_NOT_FOUND));
    }
}
