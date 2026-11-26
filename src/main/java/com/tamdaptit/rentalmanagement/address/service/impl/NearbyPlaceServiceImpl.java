package com.tamdaptit.rentalmanagement.address.service.impl;

import java.util.List;

import com.tamdaptit.rentalmanagement.address.entity.NearbyPlace;
import com.tamdaptit.rentalmanagement.address.repository.NearbyPlaceRepository;
import com.tamdaptit.rentalmanagement.address.service.NearbyPlaceService;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NearbyPlaceServiceImpl implements NearbyPlaceService {
    private final NearbyPlaceRepository places;

    public List<NearbyPlace> search(String query, String category) {
        if (query.length() > 100 || category.length() > 30) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        return places.search(query.trim(), category, PageRequest.of(0, 100));
    }
}
