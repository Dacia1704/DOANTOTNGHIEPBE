package com.tamdaptit.rentalmanagement.review.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.review.dto.ListingReviewsResponse;
import com.tamdaptit.rentalmanagement.review.dto.ListingReviewsResponse.*;
import com.tamdaptit.rentalmanagement.review.repository.*;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingReviewService {
    public enum Target { ROOM, LANDLORD }
    private final ListingRepository listings;
    private final RoomRepository rooms;
    private final RoomReviewRepository roomReviews;
    private final LandlordReviewRepository landlordReviews;
    private final Clock subscriptionClock;

    public ListingReviewsResponse get(String id, Target target, String roomId, int page, int size) {
        var listing = listings.findPublishedContact(id, LocalDateTime.now(subscriptionClock))
                .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
        var propertyId = listing.getProperty().getId();
        var ownerId = listing.getProperty().getLandlord().getId();
        var pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 20),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Item> items;
        List<RatingBucket> buckets;
        if (target == Target.LANDLORD) {
            buckets = landlordReviews.summarize(ownerId);
            items = landlordReviews.findVisible(ownerId, pageable).map(r -> new Item(r.getId(),
                    author(r.getTenant().getFullName()), r.getRating(), r.getComment(), r.getLandlordReply(),
                    r.getCreatedAt(), r.getRentalAgreement().getRoom().getRoomNumber(),
                    verified(r.getRentalAgreement(), r.getTenant().getId(), r.getRentalAgreement().getRoom().getId(), ownerId)));
        } else {
            if (roomId != null && !rooms.existsByIdAndProperty_Id(roomId, propertyId)) {
                throw new AppException(ErrorCode.ROOM_NOT_FOUND);
            }
            buckets = roomReviews.summarize(propertyId, roomId);
            items = roomReviews.findVisible(propertyId, roomId, pageable).map(r -> new Item(r.getId(),
                    author(r.getTenant().getFullName()), r.getRating(), r.getComment(), r.getLandlordReply(),
                    r.getCreatedAt(), r.getRoom().getRoomNumber(),
                    verified(r.getRentalAgreement(), r.getTenant().getId(), r.getRoom().getId(), ownerId)));
        }
        return new ListingReviewsResponse(summary(buckets), PageResponse.<Item>builder()
                .currentPage(items.getNumber()).pageSize(items.getSize()).totalPages(items.getTotalPages())
                .totalElements(items.getTotalElements()).data(items.getContent()).build());
    }

    static Summary summary(List<RatingBucket> buckets) {
        long total = buckets.stream().mapToLong(RatingBucket::getTotal).sum();
        long weighted = buckets.stream().mapToLong(b -> b.getRating() * b.getTotal()).sum();
        long replied = buckets.stream().mapToLong(b -> b.getReplied() == null ? 0 : b.getReplied()).sum();
        var distribution = IntStream.iterate(5, n -> n > 0, n -> n - 1)
                .mapToObj(stars -> new Distribution(stars, buckets.stream().filter(b -> b.getRating() == stars)
                        .mapToLong(RatingBucket::getTotal).sum())).toList();
        return new Summary(total == 0 ? null : Math.round(100.0 * weighted / total) / 100.0,
                total, total == 0 ? null : (int) Math.round(100.0 * replied / total), distribution);
    }

    private String author(String name) { return name == null || name.isBlank() ? "Người thuê" : name; }

    private boolean verified(RentalAgreement agreement, String tenantId, String roomId, String ownerId) {
        return (agreement.getStatus() == RentalAgreementStatus.ACTIVE || agreement.getStatus() == RentalAgreementStatus.TERMINATED)
                && agreement.getStartDate() != null && !agreement.getStartDate().isAfter(LocalDate.now(subscriptionClock))
                && agreement.getTenant().getId().equals(tenantId) && agreement.getRoom().getId().equals(roomId)
                && agreement.getLandlord().getId().equals(ownerId);
    }
}
