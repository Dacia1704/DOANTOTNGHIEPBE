package com.tamdaptit.rentalmanagement.listing.service;

import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingSearchRequest;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingRequest;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingContentRequest;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;

public interface ListingService {
    ListingResponse create(String landlordId, ListingRequest request);
    ListingResponse updateContent(String landlordId, String id, ListingContentRequest request);
    ListingResponse submit(String landlordId, String id);
    ListingResponse approve(String adminId, String id);
    ListingResponse reject(String adminId, String id, String reason);
    ListingResponse hide(String landlordId, String id);
    ListingResponse setPriority(String landlordId, String id, int level);
    PageResponse<ListingResponse> getPublished(int page, int size);
    PageResponse<ListingResponse> searchPublished(int page, int size,
            ListingSearchRequest request);
    PageResponse<ListingResponse> getAllForAdmin(int page, int size,
            ListingStatus status);
    ListingResponse getPublishedById(String id);
    PageResponse<ListingResponse> getMine(String landlordId, int page, int size);
    PageResponse<ListingResponse> getPending(int page, int size);
    void refreshOwner(String landlordId);
}
