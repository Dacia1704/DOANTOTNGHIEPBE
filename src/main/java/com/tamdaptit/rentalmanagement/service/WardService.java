package com.tamdaptit.rentalmanagement.service;

import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.dto.address.*;
import com.tamdaptit.rentalmanagement.entity.Ward;
import com.tamdaptit.rentalmanagement.exception.*;
import com.tamdaptit.rentalmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WardService {
    private final WardRepository wards;
    private final ProvinceRepository provinces;

    public List<ProvinceResponse> provinces() {
        return provinces.findAllByOrderByCodeAsc().stream()
                .map(p -> new ProvinceResponse(p.getId(), p.getCode(), p.getName())).toList();
    }

    public PageResponse<WardResponse> list(String provinceId, String q, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 100)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "page >= 0, size between 1 and 100, q <= 100 characters");
        }
        Specification<Ward> filter = (root, query, cb) -> cb.conjunction();
        if (provinceId != null && !provinceId.isBlank()) {
            if (!provinces.existsById(provinceId)) throw new AppException(ErrorCode.PROVINCE_NOT_FOUND);
            filter = filter.and((root, query, cb) -> cb.equal(root.get("province").get("id"), provinceId));
        }
        if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            filter = filter.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), "%" + term + "%", '\\'),
                    cb.like(root.get("code"), "%" + term + "%", '\\')));
        }
        var result = wards.findAll(filter, PageRequest.of(page, size, Sort.by("code")));
        return PageResponse.<WardResponse>builder().currentPage(page).pageSize(size)
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(WardResponse::from).toList()).build();
    }

    public WardResponse get(String id) { return WardResponse.from(find(id)); }

    @Transactional
    public WardResponse create(WardRequest request) {
        return write(new Ward(), request);
    }

    @Transactional
    public WardResponse update(String id, WardRequest request) {
        return write(find(id), request);
    }

    @Transactional
    public void delete(String id) {
        Ward ward = find(id);
        if (wards.countReferences(id) > 0) {
            throw new AppException(ErrorCode.ADDRESS_IN_USE);
        }
        wards.delete(ward);
        wards.flush();
    }

    private Ward find(String id) {
        return wards.findById(id).orElseThrow(() -> new AppException(ErrorCode.WARD_NOT_FOUND));
    }

    private WardResponse write(Ward ward, WardRequest request) {
        if (wards.existsByCodeAndIdNot(request.code(), ward.getId() == null ? "" : ward.getId())) {
            throw new AppException(ErrorCode.ADDRESS_CODE_EXISTS);
        }
        var province = provinces.findById(request.provinceId())
                .orElseThrow(() -> new AppException(ErrorCode.PROVINCE_NOT_FOUND));
        ward.setCode(request.code());
        ward.setName(request.name().trim());
        ward.setProvince(province);
        return WardResponse.from(wards.saveAndFlush(ward));
    }
}
