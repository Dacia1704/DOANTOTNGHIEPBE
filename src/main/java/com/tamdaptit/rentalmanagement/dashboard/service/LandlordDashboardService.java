package com.tamdaptit.rentalmanagement.dashboard.service;

import java.time.LocalDate;

import com.tamdaptit.rentalmanagement.dashboard.dto.response.LandlordDashboardResponse;

public interface LandlordDashboardService {
    LandlordDashboardResponse overview(
        String landlordId,
        LocalDate from,
        LocalDate to
    );
}
