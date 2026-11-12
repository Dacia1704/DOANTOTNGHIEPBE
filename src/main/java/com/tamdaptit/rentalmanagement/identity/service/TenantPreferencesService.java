package com.tamdaptit.rentalmanagement.identity.service;

import com.tamdaptit.rentalmanagement.identity.dto.request.TenantPreferences;

public interface TenantPreferencesService {
    TenantPreferences get(String userId);
    TenantPreferences save(String userId, TenantPreferences request);
}
