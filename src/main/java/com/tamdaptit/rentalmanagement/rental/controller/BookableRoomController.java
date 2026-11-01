package com.tamdaptit.rentalmanagement.rental.controller;
import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;
import com.tamdaptit.rentalmanagement.room.dto.response.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class BookableRoomController {
    private final ListingService listings;
    @GetMapping("/api/listings/{id}/bookable-rooms")
    public ApiResponse<List<RoomResponse>> get(@PathVariable String id) {
        var listing=listings.getPublishedById(id);
        if(listing.rentalMode()==PropertyRentalMode.WHOLE && listing.property().rooms().size()!=1)
            throw new AppException(ErrorCode.INVALID_REQUEST,"Cơ sở nguyên căn phải có một phòng đại diện hợp lệ");
        return ApiResponse.success(listing.property().rooms());
    }
}
