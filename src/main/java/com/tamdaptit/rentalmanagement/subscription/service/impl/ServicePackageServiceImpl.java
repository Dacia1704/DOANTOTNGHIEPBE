package com.tamdaptit.rentalmanagement.subscription.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServicePackagePriceRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServicePackageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServicePackagePriceResponse;
import com.tamdaptit.rentalmanagement.subscription.entity.ServicePackage;
import com.tamdaptit.rentalmanagement.subscription.entity.ServicePackagePrice;
import com.tamdaptit.rentalmanagement.subscription.mapper.ServicePackageMapper;
import com.tamdaptit.rentalmanagement.subscription.repository.ServicePackageRepository;
import com.tamdaptit.rentalmanagement.subscription.repository.ServicePackagePriceRepository;
import com.tamdaptit.rentalmanagement.subscription.service.ServicePackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServicePackageServiceImpl implements ServicePackageService {
    private final ServicePackageRepository packageRepository;
    private final ServicePackagePriceRepository priceRepository;
    private final ServicePackageMapper mapper;

    @Override
    public PageResponse<ServicePackageResponse> getAll(int page, int size, boolean includeInactive) {
        var result = packageRepository.findCatalog(includeInactive, PageRequest.of(
                Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name", "id")));
        return PageResponse.<ServicePackageResponse>builder()
                .currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(mapper::toResponse).toList()).build();
    }

    @Override
    public ServicePackageResponse getById(String id, boolean includeInactive) {
        ServicePackage entity = findPackage(id);
        if (!includeInactive && (!entity.getActive()
                || (!isFree(entity) && !priceRepository.existsByPackageEntity_IdAndActiveTrue(id)))) {
            throw new AppException(ErrorCode.SERVICE_PACKAGE_NOT_FOUND);
        }
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional
    public ServicePackageResponse create(ServicePackageRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (packageRepository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.SERVICE_PACKAGE_CODE_EXISTS);
        }
        validateRights(request);
        if ("FREE".equals(code)) {
            validateFree(request);
        }
        ServicePackage entity = mapper.toEntity(request);
        entity.setCode(code);
        entity.setName(request.name().trim());
        return mapper.toResponse(packageRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public ServicePackageResponse update(String id, ServicePackageRequest request) {
        ServicePackage entity = findPackage(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (isFree(entity)) {
            validateFree(request);
        }
        if (packageRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new AppException(ErrorCode.SERVICE_PACKAGE_CODE_EXISTS);
        }
        validateRights(request);
        mapper.update(request, entity);
        entity.setCode(code);
        entity.setName(request.name().trim());
        return mapper.toResponse(packageRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public ServicePackageResponse setActive(String id, boolean active) {
        ServicePackage entity = findPackage(id);
        if (isFree(entity) && !active) {
            throw new AppException(ErrorCode.DEFAULT_SERVICE_PACKAGE_PROTECTED);
        }
        entity.setActive(active);
        return mapper.toResponse(packageRepository.saveAndFlush(entity));
    }

    @Override
    public List<ServicePackagePriceResponse> getPrices(String packageId, boolean includeInactive) {
        ServicePackage entity = findPackage(packageId);
        if (!includeInactive && !entity.getActive()) {
            throw new AppException(ErrorCode.SERVICE_PACKAGE_NOT_FOUND);
        }
        return priceRepository.findCatalog(packageId, includeInactive).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ServicePackagePriceResponse createPrice(String packageId, ServicePackagePriceRequest request) {
        ServicePackage entity = findPaidPackage(packageId);
        validateDuration(request.durationMonths());
        if (priceRepository.existsByPackageEntity_IdAndDurationMonths(packageId, request.durationMonths())) {
            throw new AppException(ErrorCode.SERVICE_PACKAGE_PRICE_EXISTS);
        }
        ServicePackagePrice price = mapper.toEntity(request);
        price.setPackageEntity(entity);
        return mapper.toResponse(priceRepository.saveAndFlush(price));
    }

    @Override
    @Transactional
    public ServicePackagePriceResponse updatePrice(String packageId, String priceId, ServicePackagePriceRequest request) {
        findPaidPackage(packageId);
        ServicePackagePrice price = findPrice(packageId, priceId);
        validateDuration(request.durationMonths());
        if (priceRepository.existsByPackageEntity_IdAndDurationMonthsAndIdNot(
                packageId, request.durationMonths(), priceId)) {
            throw new AppException(ErrorCode.SERVICE_PACKAGE_PRICE_EXISTS);
        }
        mapper.update(request, price);
        return mapper.toResponse(priceRepository.saveAndFlush(price));
    }

    @Override
    @Transactional
    public ServicePackagePriceResponse setPriceActive(String packageId, String priceId, boolean active) {
        findPaidPackage(packageId);
        ServicePackagePrice price = findPrice(packageId, priceId);
        price.setActive(active);
        return mapper.toResponse(priceRepository.saveAndFlush(price));
    }

    private ServicePackage findPackage(String id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PACKAGE_NOT_FOUND));
    }

    private ServicePackage findPaidPackage(String id) {
        ServicePackage entity = findPackage(id);
        if (isFree(entity)) {
            throw new AppException(ErrorCode.DEFAULT_SERVICE_PACKAGE_PROTECTED);
        }
        return entity;
    }

    private ServicePackagePrice findPrice(String packageId, String priceId) {
        return priceRepository.findByIdAndPackageEntity_Id(priceId, packageId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PACKAGE_PRICE_NOT_FOUND));
    }

    private boolean isFree(ServicePackage entity) {
        return "FREE".equalsIgnoreCase(entity.getCode());
    }

    private void validateDuration(Integer duration) {
        if (duration == null || !Set.of(1, 3, 6, 12).contains(duration)) {
            throw new AppException(ErrorCode.INVALID_PACKAGE_DURATION);
        }
    }

    private void validateRights(ServicePackageRequest request) {
        long slots = (long) request.highPrioritySlots() + request.lowPrioritySlots();
        if ((request.maxListings() != null && slots > request.maxListings())
                || (request.defaultPriorityLevel() == 1 && request.lowPrioritySlots() != 0)) {
            throw new AppException(ErrorCode.INVALID_SERVICE_PACKAGE);
        }
    }

    private void validateFree(ServicePackageRequest request) {
        if (!"FREE".equalsIgnoreCase(request.code()) || !Integer.valueOf(3).equals(request.maxListings())
                || request.highPrioritySlots() != 0 || request.lowPrioritySlots() != 0
                || request.defaultPriorityLevel() != 0 || request.hasStats() || !request.active()) {
            throw new AppException(ErrorCode.DEFAULT_SERVICE_PACKAGE_PROTECTED);
        }
    }
}
