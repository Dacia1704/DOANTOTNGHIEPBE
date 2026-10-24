package com.tamdaptit.rentalmanagement.subscription.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.request.ServiceSubscriptionOrderRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.*;

public interface ServiceSubscriptionService {
    ServiceSubscriptionOrderResponse createOrder(String landlordId, ServiceSubscriptionOrderRequest request);
    CurrentSubscriptionResponse getCurrent(String landlordId);
    PageResponse<ServiceSubscriptionResponse> getHistory(String landlordId, int page, int size);
    PageResponse<ServiceSubscriptionOrderResponse> getOrders(String landlordId, int page, int size);
    ServiceSubscriptionOrderResponse getOrder(String landlordId, String orderId);
    PageResponse<ServiceSubscriptionPeriodResponse> getPeriods(String landlordId, int page, int size);
    PageResponse<ServiceSubscriptionOrderResponse> getAwaitingConfirmation(int page, int size);
    ServiceSubscriptionOrderResponse confirmChange(String adminId, String orderId);

    // Internal payment integration only; never expose an endpoint to mark an order as paid.
    ServiceSubscriptionOrderResponse applyPaidOrder(String landlordId, String orderId);
    void synchronizeOwner(String landlordId);
}
