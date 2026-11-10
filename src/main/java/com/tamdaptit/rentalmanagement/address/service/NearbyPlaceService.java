package com.tamdaptit.rentalmanagement.address.service;

import com.tamdaptit.rentalmanagement.address.entity.NearbyPlace;
import java.util.List;

public interface NearbyPlaceService {
    List<NearbyPlace> search(String query, String category);
}
