package com.tamdaptit.rentalmanagement.subscription.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostServicePackageResponse;
import com.tamdaptit.rentalmanagement.subscription.entity.BoostServicePackage;
import com.tamdaptit.rentalmanagement.subscription.mapper.BoostServicePackageMapper;
import com.tamdaptit.rentalmanagement.subscription.repository.BoostServicePackageRepository;
import com.tamdaptit.rentalmanagement.subscription.service.BoostServicePackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoostServicePackageServiceImpl implements BoostServicePackageService {
    private final BoostServicePackageRepository repository;
    private final BoostServicePackageMapper mapper;

    @Override
    public PageResponse<BoostServicePackageResponse> getAll(int page, int size, boolean includeInactive) {
        var result = repository.findCatalog(includeInactive, PageRequest.of(
                Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("durationMonths", "code", "id")));
        return PageResponse.<BoostServicePackageResponse>builder()
                .currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(mapper::toResponse).toList()).build();
    }

    @Override
    public BoostServicePackageResponse getById(String id, boolean includeInactive) {
        BoostServicePackage entity = find(id);
        if (!includeInactive && (!entity.getActive() || entity.getDurationMonths() == null
                || !Set.of(1, 3, 6).contains(entity.getDurationMonths())
                || (entity.getHighPrioritySlots() == 0 && entity.getLowPrioritySlots() == 0))) {
            throw new AppException(ErrorCode.BOOST_PACKAGE_NOT_FOUND);
        }
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional
    public BoostServicePackageResponse create(BoostServicePackageRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.BOOST_PACKAGE_CODE_EXISTS);
        }
        validateDuration(request.durationMonths());
        validateSlots(request.highPrioritySlots(), request.lowPrioritySlots());
        BoostServicePackage entity = mapper.toEntity(request);
        entity.setCode(code);
        entity.setName(request.name().trim());
        return mapper.toResponse(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public BoostServicePackageResponse update(String id, BoostServicePackageRequest request) {
        BoostServicePackage entity = find(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AppException(ErrorCode.BOOST_PACKAGE_CODE_EXISTS);
        }
        validateDuration(request.durationMonths());
        validateSlots(request.highPrioritySlots(), request.lowPrioritySlots());
        mapper.update(request, entity);
        entity.setCode(code);
        entity.setName(request.name().trim());
        return mapper.toResponse(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public BoostServicePackageResponse setActive(String id, boolean active) {
        BoostServicePackage entity = find(id);
        if (active) {
            validateDuration(entity.getDurationMonths());
            validateSlots(entity.getHighPrioritySlots(), entity.getLowPrioritySlots());
        }
        entity.setActive(active);
        return mapper.toResponse(repository.saveAndFlush(entity));
    }

    private BoostServicePackage find(String id) {
        return repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.BOOST_PACKAGE_NOT_FOUND));
    }

    private void validateSlots(Integer high, Integer low) {
        if (high == null || low == null || high < 0 || low < 0 || (long) high + low == 0) {
            throw new AppException(ErrorCode.INVALID_BOOST_PACKAGE);
        }
    }

    private void validateDuration(Integer duration) {
        if (duration == null || !Set.of(1, 3, 6).contains(duration)) {
            throw new AppException(ErrorCode.INVALID_PACKAGE_DURATION);
        }
    }
}
