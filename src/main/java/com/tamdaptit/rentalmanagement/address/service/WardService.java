package com.tamdaptit.rentalmanagement.address.service;

import java.util.List;

import com.tamdaptit.rentalmanagement.address.dto.request.WardRequest;
import com.tamdaptit.rentalmanagement.address.dto.response.ProvinceResponse;
import com.tamdaptit.rentalmanagement.address.dto.response.WardResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;

public interface WardService {
    List<ProvinceResponse> provinces();

    PageResponse<WardResponse> list(
        String provinceId,
        String q,
        int page,
        int size
    );

    WardResponse get(String id);

    WardResponse create(WardRequest request);

    WardResponse update(String id, WardRequest request);

    void delete(String id);
}
