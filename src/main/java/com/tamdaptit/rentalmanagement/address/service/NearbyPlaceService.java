package com.tamdaptit.rentalmanagement.address.service;

import java.util.List;

import com.tamdaptit.rentalmanagement.address.entity.NearbyPlace;

public interface NearbyPlaceService {
    List<NearbyPlace> search(String query, String category);
}
