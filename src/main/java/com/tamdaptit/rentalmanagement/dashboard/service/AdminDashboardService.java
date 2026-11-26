package com.tamdaptit.rentalmanagement.dashboard.service;

import java.time.LocalDate;

import com.tamdaptit.rentalmanagement.dashboard.dto.response.AdminDashboardResponse;

public interface AdminDashboardService {
    AdminDashboardResponse overview(LocalDate from, LocalDate to);
}
