package com.tamdaptit.rentalmanagement.subscription.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServicePackageRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServicePackagePriceRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServicePackageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ServicePackagePriceResponse;
import java.util.List;

public interface ServicePackageService {
    PageResponse<ServicePackageResponse> getAll(int page, int size, boolean includeInactive);
    ServicePackageResponse getById(String id, boolean includeInactive);
    ServicePackageResponse create(ServicePackageRequest request);
    ServicePackageResponse update(String id, ServicePackageRequest request);
    ServicePackageResponse setActive(String id, boolean active);
    List<ServicePackagePriceResponse> getPrices(String packageId, boolean includeInactive);
    ServicePackagePriceResponse createPrice(String packageId, ServicePackagePriceRequest request);
    ServicePackagePriceResponse updatePrice(String packageId, String priceId, ServicePackagePriceRequest request);
    ServicePackagePriceResponse setPriceActive(String packageId, String priceId, boolean active);
}
