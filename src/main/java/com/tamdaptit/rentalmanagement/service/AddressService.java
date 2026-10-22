package com.tamdaptit.rentalmanagement.service;

import com.tamdaptit.rentalmanagement.dto.address.AddressSnapshot;
import java.io.IOException;

public interface AddressService {
    AddressSnapshot fetch();

    AddressSnapshot parse(String json) throws IOException;
}
