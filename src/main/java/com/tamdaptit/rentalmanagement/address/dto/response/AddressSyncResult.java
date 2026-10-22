package com.tamdaptit.rentalmanagement.address.dto.response;

public record AddressSyncResult(int provincesCreated, int provincesUpdated,
                                int wardsCreated, int wardsUpdated) {
}
