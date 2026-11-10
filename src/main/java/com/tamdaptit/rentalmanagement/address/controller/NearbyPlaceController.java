package com.tamdaptit.rentalmanagement.address.controller;

import com.tamdaptit.rentalmanagement.address.entity.NearbyPlace;
import com.tamdaptit.rentalmanagement.address.service.NearbyPlaceService;
import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/nearby-places")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NearbyPlaceController {
    private final NearbyPlaceService places;
    @GetMapping
    public ApiResponse<List<NearbyPlace>> search(@RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "") @Size(max = 30) String category) {
        return ApiResponse.success(places.search(q, category));
    }
}
