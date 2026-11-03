package com.tamdaptit.rentalmanagement.listing.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingPhoneResponse;
import com.tamdaptit.rentalmanagement.listing.service.ListingContactService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingContactController {
    private final ListingContactService contacts;

    @PostMapping("/{id}/reveal-phone")
    public ApiResponse<ListingPhoneResponse> revealPhone(@PathVariable String id, Principal principal) {
        return ApiResponse.success(contacts.revealPhone(id, principal == null ? null : principal.getName()));
    }
}
