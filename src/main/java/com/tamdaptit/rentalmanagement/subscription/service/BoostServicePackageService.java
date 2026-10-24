package com.tamdaptit.rentalmanagement.subscription.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostServicePackageResponse;

public interface BoostServicePackageService {
    PageResponse<BoostServicePackageResponse> getAll(int page, int size, boolean includeInactive);
    BoostServicePackageResponse getById(String id, boolean includeInactive);
    BoostServicePackageResponse create(BoostServicePackageRequest request);
    BoostServicePackageResponse update(String id, BoostServicePackageRequest request);
    BoostServicePackageResponse setActive(String id, boolean active);
}
