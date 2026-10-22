package com.tamdaptit.rentalmanagement.service;

import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.dto.address.*;
import java.util.List;

public interface WardService {
    List<ProvinceResponse> provinces();

    PageResponse<WardResponse> list(String provinceId, String q, int page, int size);

    WardResponse get(String id);

    WardResponse create(WardRequest request);

    WardResponse update(String id, WardRequest request);

    void delete(String id);

}
