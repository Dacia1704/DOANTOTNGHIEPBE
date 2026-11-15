package com.tamdaptit.rentalmanagement.dashboard.service;

import com.tamdaptit.rentalmanagement.dashboard.dto.response.LandlordDashboardResponse;
import java.time.LocalDate;

public interface LandlordDashboardService {
    LandlordDashboardResponse overview(String landlordId, LocalDate from, LocalDate to);
}
