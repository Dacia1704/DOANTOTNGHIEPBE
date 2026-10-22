package com.tamdaptit.rentalmanagement.address.service;

import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot;
import com.tamdaptit.rentalmanagement.address.dto.response.AddressSyncResult;

public interface AddressImporter {
    AddressSyncResult apply(AddressSnapshot snapshot);

}
