package com.tamdaptit.rentalmanagement.service;

import com.tamdaptit.rentalmanagement.dto.address.AddressSnapshot;
import com.tamdaptit.rentalmanagement.dto.address.AddressSyncResult;

public interface AddressImporter {
    AddressSyncResult apply(AddressSnapshot snapshot);

}
