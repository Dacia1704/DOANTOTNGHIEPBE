package com.tamdaptit.rentalmanagement.dashboard.service;

import com.tamdaptit.rentalmanagement.dashboard.dto.response.AdminDashboardResponse;
import java.time.LocalDate;

public interface AdminDashboardService {
    AdminDashboardResponse overview(LocalDate from, LocalDate to);
}
