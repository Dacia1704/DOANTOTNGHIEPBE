package com.tamdaptit.rentalmanagement.address.service;

import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot;
import java.io.IOException;

public interface AddressService {
    AddressSnapshot fetch();

    AddressSnapshot parse(String json) throws IOException;
}
