package com.tamdaptit.rentalmanagement.address.service;

import java.io.IOException;

import com.tamdaptit.rentalmanagement.address.dto.response.AddressSnapshot;

public interface AddressService {
    AddressSnapshot fetch();

    AddressSnapshot parse(String json) throws IOException;
}
