package com.tamdaptit.rentalmanagement.identity.entity.enums;

/**
 * Nhom nguoi thue. Dung de gan "gu" mac dinh khi user chua co hanh vi
 * (cold start) va de goi y gia tri cho form.
 */
public enum TenantType {
    STUDENT,
    WORKER,
    FAMILY,
    OTHER
}
