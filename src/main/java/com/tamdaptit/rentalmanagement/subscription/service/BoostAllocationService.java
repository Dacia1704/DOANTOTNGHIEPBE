package com.tamdaptit.rentalmanagement.subscription.service;

import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostAllocationRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.ActiveBoostResponse;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostAllocationResponse;
import java.util.List;

public interface BoostAllocationService {
    List<ActiveBoostResponse> getActive(String landlordId);
    BoostAllocationResponse assign(String landlordId, String listingId, BoostAllocationRequest request);
    void unassign(String landlordId, String listingId);
}
