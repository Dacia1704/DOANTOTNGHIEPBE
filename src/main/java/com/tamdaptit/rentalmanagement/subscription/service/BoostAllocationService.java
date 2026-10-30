package com.tamdaptit.rentalmanagement.subscription.service;

import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostAllocationRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.*;
import com.tamdaptit.rentalmanagement.subscription.entity.BoostAllocation;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.BoostSubscriptionStatus;
import com.tamdaptit.rentalmanagement.subscription.mapper.BoostPurchaseMapper;
import com.tamdaptit.rentalmanagement.subscription.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BoostAllocationService {
    private final UserRepository users;
    private final ListingRepository listings;
    private final ListingService listingService;
    private final BoostPurchaseRepository boosts;
    private final BoostAllocationRepository allocations;
    private final BoostPurchaseMapper mapper;
    private final Clock subscriptionClock;

    private LocalDateTime prepare(String landlordId) {
        var owner = users.findByIdForUpdate(landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (owner.getStatus() != UserStatus.ACTIVE) throw new AppException(ErrorCode.USER_INACTIVE);
        var time = LocalDateTime.now(subscriptionClock).truncatedTo(ChronoUnit.SECONDS);
        boosts.findByLandlord_IdAndStatus(landlordId, BoostSubscriptionStatus.ACTIVE).stream()
                .filter(b -> !time.isBefore(b.getEndAt())).forEach(b -> b.setStatus(BoostSubscriptionStatus.EXPIRED));
        boosts.flush();
        allocations.deleteAll(allocations.findStale(landlordId, time));
        allocations.flush();
        return time;
    }

    public List<ActiveBoostResponse> getActive(String landlordId) {
        var time = prepare(landlordId);
        return boosts.findByLandlord_IdAndStatus(landlordId, BoostSubscriptionStatus.ACTIVE).stream()
                .filter(b -> !time.isBefore(b.getStartAt()) && time.isBefore(b.getEndAt()))
                .map(b -> {
                    var assigned = allocations.findBySubscription_Id(b.getId());
                    long high = assigned.stream().filter(a -> a.getPriorityLevel() == 2).count();
                    long low = assigned.stream().filter(a -> a.getPriorityLevel() == 1).count();
                    return new ActiveBoostResponse(mapper.toResponse(b), high, low,
                            b.getHighPrioritySlotsSnapshot() - high, b.getLowPrioritySlotsSnapshot() - low,
                            assigned.stream().map(this::response).toList());
                }).toList();
    }

    public BoostAllocationResponse assign(String landlordId, String listingId, BoostAllocationRequest request) {
        var time = prepare(landlordId);
        if (request.level() == null || request.level() < 1 || request.level() > 2)
            throw new AppException(ErrorCode.INVALID_REQUEST);
        var listing = listings.findByIdAndProperty_Landlord_Id(listingId, landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
        if (listing.getStatus() != ListingStatus.PUBLISHED || listing.getExpiresAt() == null
                || !time.isBefore(listing.getExpiresAt())) throw new AppException(ErrorCode.INVALID_LISTING_STATUS);
        var boost = boosts.findByIdAndLandlord_Id(request.boostSubscriptionId(), landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (boost.getStatus() != BoostSubscriptionStatus.ACTIVE || time.isBefore(boost.getStartAt())
                || !time.isBefore(boost.getEndAt())) throw new AppException(ErrorCode.SUBSCRIPTION_EXPIRED);
        long used = allocations.findBySubscription_Id(boost.getId()).stream()
                .filter(a -> !a.getListing().getId().equals(listingId) && a.getPriorityLevel().equals(request.level())).count();
        int limit = request.level() == 2 ? boost.getHighPrioritySlotsSnapshot() : boost.getLowPrioritySlotsSnapshot();
        if (used >= limit) throw new AppException(ErrorCode.BOOST_SLOTS_EXCEEDED);
        var allocation = allocations.findByListing_Id(listingId).orElseGet(BoostAllocation::new);
        allocation.setSubscription(boost);
        allocation.setListing(listing);
        allocation.setPriorityLevel(request.level());
        allocations.saveAndFlush(allocation);
        listingService.refreshOwner(landlordId);
        return response(allocation);
    }

    public void unassign(String landlordId, String listingId) {
        prepare(landlordId);
        listings.findByIdAndProperty_Landlord_Id(listingId, landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
        allocations.findByListing_Id(listingId).ifPresent(allocations::delete);
        allocations.flush();
        listingService.refreshOwner(landlordId);
    }

    private BoostAllocationResponse response(BoostAllocation allocation) {
        return new BoostAllocationResponse(allocation.getListing().getId(), allocation.getSubscription().getId(),
                allocation.getPriorityLevel());
    }
}
