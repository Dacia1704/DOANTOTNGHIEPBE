package com.tamdaptit.rentalmanagement.subscription.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.payment.repository.PaymentRepository;
import com.tamdaptit.rentalmanagement.subscription.dto.request.BoostPurchaseRequest;
import com.tamdaptit.rentalmanagement.subscription.dto.response.BoostPurchaseResponse;
import com.tamdaptit.rentalmanagement.subscription.entity.BoostServicePackageSubscription;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.BoostSubscriptionStatus;
import com.tamdaptit.rentalmanagement.subscription.mapper.BoostPurchaseMapper;
import com.tamdaptit.rentalmanagement.subscription.repository.BoostPurchaseRepository;
import com.tamdaptit.rentalmanagement.subscription.repository.BoostServicePackageRepository;
import com.tamdaptit.rentalmanagement.subscription.service.BoostPurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoostPurchaseServiceImpl implements BoostPurchaseService {
    private final UserRepository users;
    private final ListingRepository listings;
    private final ListingService listingService;
    private final BoostPurchaseRepository repository;
    private final BoostServicePackageRepository packages;
    private final PaymentRepository payments;
    private final BoostPurchaseMapper mapper;
    private final Clock subscriptionClock;

    private LocalDateTime now() {
        return LocalDateTime.now(subscriptionClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private User lockOwner(String landlordId) {
        return users.findByIdForUpdate(landlordId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean validListing(Listing listing, LocalDateTime time) {
        return listing.getStatus() == ListingStatus.PUBLISHED && listing.getExpiresAt() != null && time.isBefore(listing.getExpiresAt());
    }

    private List<BoostServicePackageSubscription> active(String landlordId, LocalDateTime time) {
        var active = repository.findByLandlord_IdAndStatus(landlordId, BoostSubscriptionStatus.ACTIVE);
        active.stream().filter(b -> !time.isBefore(b.getEndAt())).forEach(b -> b.setStatus(BoostSubscriptionStatus.EXPIRED));
        repository.flush();
        return active.stream().filter(b -> b.getStatus() == BoostSubscriptionStatus.ACTIVE).toList();
    }

    @Override
    @Transactional
    public BoostPurchaseResponse create(String landlordId, BoostPurchaseRequest request) {
        User owner = lockOwner(landlordId);
        if (owner.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        var listing = listings.findByIdAndRoom_Landlord_Id(request.listingId(), landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
        LocalDateTime time = now();
        if (!validListing(listing, time)) {
            throw new AppException(ErrorCode.INVALID_LISTING_STATUS);
        }
        if (active(landlordId, time).stream().anyMatch(b -> b.getListing().getId().equals(listing.getId()))) {
            throw new AppException(ErrorCode.BOOST_ALREADY_ACTIVE);
        }
        var catalog = packages.findById(request.boostPackageId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOST_PACKAGE_NOT_FOUND));
        if (!Boolean.TRUE.equals(catalog.getActive()) || catalog.getDurationMonths() == null) {
            throw new AppException(ErrorCode.BOOST_PACKAGE_NOT_FOUND);
        }
        return mapper.toResponse(repository.saveAndFlush(BoostServicePackageSubscription.builder()
                .landlord(owner).listing(listing).boostPackage(catalog).price(catalog.getPrice())
                .packageNameSnapshot(catalog.getName()).priorityLevelSnapshot(2)
                .durationMonthsSnapshot(catalog.getDurationMonths()).paymentExpiresAt(time.plusMinutes(15)).build()));
    }

    @Override
    @Transactional
    public boolean applyPaidBoost(String landlordId, String boostId) {
        User owner = lockOwner(landlordId);
        var boost = repository.findByIdAndLandlord_Id(boostId, landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        var paid = payments.findByBoostSubscription_IdAndStatus(boostId, PaymentStatus.SUCCESS).stream()
                .filter(payment -> !Boolean.TRUE.equals(payment.getRequiresReview())).toList();
        if (paid.size() != 1 || !paid.getFirst().getLandlord().getId().equals(landlordId)
                || paid.getFirst().getAmount().compareTo(boost.getPrice()) != 0 || paid.getFirst().getPaidAt() == null) {
            throw new AppException(ErrorCode.SUBSCRIPTION_ORDER_NOT_PAID);
        }
        if (boost.getStartAt() != null && (boost.getStatus() == BoostSubscriptionStatus.ACTIVE
                || boost.getStatus() == BoostSubscriptionStatus.EXPIRED)) {
            return true;
        }
        LocalDateTime time = now();
        var listing = listings.findByIdAndRoom_Landlord_Id(boost.getListing().getId(), landlordId).orElseThrow();
        boolean overlaps = active(landlordId, time).stream().anyMatch(b -> b.getListing().getId().equals(listing.getId()));
        if (owner.getStatus() != UserStatus.ACTIVE || !validListing(listing, time) || overlaps
                || !paid.getFirst().getPaidAt().isBefore(boost.getPaymentExpiresAt())
                || !paid.getFirst().getPaidAt().isBefore(paid.getFirst().getExpiresAt())) {
            boost.setStatus(BoostSubscriptionStatus.CANCELLED);
            repository.saveAndFlush(boost);
            return false;
        }
        boost.setStatus(BoostSubscriptionStatus.ACTIVE);
        boost.setStartAt(time);
        boost.setEndAt(time.plusMonths(boost.getDurationMonthsSnapshot()));
        repository.saveAndFlush(boost);
        listingService.refreshOwner(landlordId);
        return true;
    }

    @Override
    public PageResponse<BoostPurchaseResponse> getHistory(String landlordId, int page, int size) {
        var result = repository.findByLandlord_Id(landlordId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.<BoostPurchaseResponse>builder().currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(mapper::toResponse).toList()).build();
    }
}
