package com.tamdaptit.rentalmanagement.subscription.service;

import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostPurchaseRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostPurchaseResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;

public interface BoostPurchaseService {
    BoostPurchaseResponse create(String landlordId, BoostPurchaseRequest request);
    PageResponse<BoostPurchaseResponse> getHistory(String landlordId, int page, int size);
    boolean applyPaidBoost(String landlordId, String boostId);
}
