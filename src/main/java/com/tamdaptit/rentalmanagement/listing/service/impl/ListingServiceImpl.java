package com.tamdaptit.rentalmanagement.listing.service.impl;

import com.tamdaptit.rentalmanagement.room.repository.RoomImageRepository;
import com.tamdaptit.rentalmanagement.room.repository.RoomAmenityRepository;
import com.tamdaptit.rentalmanagement.room.mapper.RoomMapper;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingSearchRequest;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingRequest;
import com.tamdaptit.rentalmanagement.listing.dto.request.ListingContentRequest;
import com.tamdaptit.rentalmanagement.listing.dto.response.ListingResponse;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.mapper.ListingMapper;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import com.tamdaptit.rentalmanagement.subscription.entity.ServiceSubscriptionPeriod;
import com.tamdaptit.rentalmanagement.subscription.repository.BoostAllocationRepository;
import com.tamdaptit.rentalmanagement.subscription.repository.ServiceSubscriptionPeriodRepository;
import com.tamdaptit.rentalmanagement.subscription.service.ServiceSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingServiceImpl implements ListingService {
    private final ListingRepository repository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final ServiceSubscriptionService subscriptions;
    private final ServiceSubscriptionPeriodRepository periods;
    private final BoostAllocationRepository boosts;
    private final ListingMapper mapper;
    private final Clock subscriptionClock;
    private final RoomMapper roomMapper;
    private final RoomAmenityRepository roomAmenities;
    private final RoomImageRepository roomImages;
    private final com.tamdaptit.rentalmanagement.property.repository.PropertyRepository properties;
    private final com.tamdaptit.rentalmanagement.property.repository.PropertyAmenityRepository propertyAmenities;
    private final com.tamdaptit.rentalmanagement.property.repository.PropertyImageRepository propertyImages;
    private final com.tamdaptit.rentalmanagement.property.mapper.PropertyMapper propertyMapper;

    private LocalDateTime now() {
        return LocalDateTime.now(subscriptionClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private User lockOwner(String id) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        return user;
    }

    private Listing findOwned(String ownerId, String id) {
        return repository.findByIdAndProperty_Landlord_Id(id, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
    }

    @Override
    @Transactional
    public ListingResponse create(String landlordId, ListingRequest request) {
        lockOwner(landlordId);
        var property = properties.findLockedById(request.propertyId())
                .orElseThrow(() -> new AppException(ErrorCode.PROPERTY_NOT_FOUND));
        if (!property.getLandlord().getId().equals(landlordId)) throw new AppException(ErrorCode.NOT_PROPERTY_OWNER);
        var listing = Listing.builder().property(property).title(request.title().trim()).description(request.description())
                .slug(UUID.randomUUID().toString()).build();
        submit(listing, landlordId, now());
        return response(repository.saveAndFlush(listing));
    }

    private void checkQuota(Listing listing, String landlordId, LocalDateTime time) {
        var current = subscriptions.getCurrent(landlordId);
        if (!current.canSubmitListings()) {
            throw new AppException(ErrorCode.NO_ACTIVE_SUBSCRIPTION);
        }
        String excludeId = listing.getId() == null ? "" : listing.getId();
        var quota = current.rights().maxListings();
        // Locking reads see the latest committed rows even when an admin transaction read before waiting for the owner lock.
        var live = repository.findByProperty_Landlord_Id(landlordId).stream()
                .filter(l -> !l.getId().equals(excludeId) && holdsQuota(l, time)).toList();
        if (quota != null && live.size() + 1 > quota) {
            throw new AppException(ErrorCode.QUOTA_EXCEEDED);
        }
        if (live.stream().anyMatch(l -> l.getProperty().getId().equals(listing.getProperty().getId()))) {
            throw new AppException(ErrorCode.ACTIVE_LISTING_EXISTED);
        }
        if (!roomRepository.existsByProperty_IdAndRentalStatus(listing.getProperty().getId(), RoomRentalStatus.AVAILABLE)) {
            throw new AppException(ErrorCode.ROOM_NOT_AVAILABLE);
        }
    }

    private void submit(Listing listing, String landlordId, LocalDateTime time) {
        checkQuota(listing, landlordId, time);
        listing.setStatus(ListingStatus.PENDING);
        listing.setSubmittedAt(time);
        listing.setPendingExpiresAt(time.plusDays(7));
        listing.setRejectionReason(null);
        listing.setApprovedBy(null);
        listing.setApprovedAt(null);
        clearSelection(listing);
        listing.setPriorityLevel(0);
    }

    private boolean holdsQuota(Listing listing, LocalDateTime time) {
        return (listing.getStatus() == ListingStatus.PENDING && listing.getPendingExpiresAt() != null
                && time.isBefore(listing.getPendingExpiresAt())) || published(listing, time);
    }

    private boolean published(Listing listing, LocalDateTime time) {
        return listing.getStatus() == ListingStatus.PUBLISHED && listing.getExpiresAt() != null
                && time.isBefore(listing.getExpiresAt());
    }

    @Override
    @Transactional
    public ListingResponse updateContent(String landlordId, String id, ListingContentRequest request) {
        lockOwner(landlordId);
        var listing = findOwned(landlordId, id);
        // Editing a live/pending listing must not bypass the content that the admin reviews.
        if (holdsQuota(listing, now())) {
            throw new AppException(ErrorCode.INVALID_LISTING_STATUS);
        }
        listing.setTitle(request.title().trim());
        listing.setDescription(request.description());
        return response(repository.saveAndFlush(listing));
    }

    @Override
    @Transactional
    public ListingResponse submit(String landlordId, String id) {
        lockOwner(landlordId);
        var listing = findOwned(landlordId, id);
        LocalDateTime time = now();
        if (holdsQuota(listing, time)) {
            throw new AppException(ErrorCode.INVALID_LISTING_STATUS);
        }
        submit(listing, landlordId, time);
        return response(repository.saveAndFlush(listing));
    }

    private User admin(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        return user;
    }

    private Listing pendingForAdmin(String id) {
        String ownerId = repository.findLandlordIdById(id)
                .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
        lockOwner(ownerId);
        var listing = findOwned(ownerId, id);
        if (listing.getStatus() != ListingStatus.PENDING || listing.getPendingExpiresAt() == null
                || !now().isBefore(listing.getPendingExpiresAt())) {
            throw new AppException(ErrorCode.LISTING_NOT_PENDING);
        }
        return listing;
    }

    @Override
    @Transactional
    public ListingResponse approve(String adminId, String id) {
        User admin = admin(adminId);
        var listing = pendingForAdmin(id);
        LocalDateTime time = now();
        checkQuota(listing, listing.getProperty().getLandlord().getId(), time);
        listing.setStatus(ListingStatus.PUBLISHED);
        listing.setApprovedBy(admin);
        listing.setApprovedAt(time);
        listing.setPublishedAt(time);
        listing.setExpiresAt(time.plusMonths(1));
        listing.setRejectionReason(null);
        repository.saveAndFlush(listing);
        listing.setPriorityLevel(response(listing).priorityLevel());
        return response(repository.saveAndFlush(listing));
    }

    @Override
    @Transactional
    public ListingResponse reject(String adminId, String id, String reason) {
        admin(adminId);
        var listing = pendingForAdmin(id);
        listing.setStatus(ListingStatus.REJECTED);
        listing.setRejectionReason(reason.trim());
        listing.setPriorityLevel(0);
        clearSelection(listing);
        return response(repository.saveAndFlush(listing));
    }

    @Override
    @Transactional
    public ListingResponse hide(String landlordId, String id) {
        lockOwner(landlordId);
        var listing = findOwned(landlordId, id);
        listing.setStatus(ListingStatus.HIDDEN);
        boosts.findByListing_Id(id).ifPresent(boosts::delete);
        listing.setPriorityLevel(0);
        clearSelection(listing);
        return response(repository.saveAndFlush(listing));
    }

    @Override
    @Transactional
    public ListingResponse setPriority(String landlordId, String id, int level) {
        lockOwner(landlordId);
        var listing = findOwned(landlordId, id);
        if (level < 0 || level > 2) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        LocalDateTime time = now();
        if (level == 0) {
            clearSelection(listing);
        } else {
            if (!published(listing, time)) {
                throw new AppException(ErrorCode.INVALID_LISTING_STATUS);
            }
            var current = subscriptions.getCurrent(landlordId);
            if (!current.canSubmitListings() || current.currentPeriod() == null) {
                throw new AppException(ErrorCode.NO_ACTIVE_SUBSCRIPTION);
            }
            if (level == 1 && current.rights().defaultPriorityLevel() == 1) {
                clearSelection(listing);
            } else {
                int limit = level == 2 ? current.rights().highPrioritySlots() : current.rights().lowPrioritySlots();
                String periodId = current.currentPeriod().id();
                long used = repository.findByProperty_Landlord_Id(landlordId).stream()
                        .filter(l -> !l.getId().equals(id) && published(l, time) && l.getServiceSubscriptionPeriod() != null
                                && l.getServiceSubscriptionPeriod().getId().equals(periodId) && l.getPackagePriorityLevel() == level)
                        .count();
                if (used + 1 > limit) {
                    throw new AppException(ErrorCode.PRIORITY_SLOTS_EXCEEDED);
                }
                listing.setServiceSubscriptionPeriod(periods.findById(periodId).orElseThrow());
                listing.setPackagePriorityLevel(level);
            }
        }
        repository.saveAndFlush(listing);
        listing.setPriorityLevel(response(listing).priorityLevel());
        return response(repository.saveAndFlush(listing));
    }

    private void clearSelection(Listing listing) {
        listing.setPackagePriorityLevel(0);
        listing.setServiceSubscriptionPeriod(null);
    }

    @Override
    public PageResponse<ListingResponse> getPublished(int page, int size) {
        LocalDateTime time = now();
        return page(repository.findPublished(time, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100))), time);
    }

    @Override
    public PageResponse<ListingResponse> searchPublished(int page, int size,
            ListingSearchRequest request) {
        if (page < 0 || size < 1 || size > 100
                || (request.minPrice() != null && request.minPrice().signum() < 0)
                || (request.maxPrice() != null && request.maxPrice().signum() < 0)
                || (request.minPrice() != null && request.maxPrice() != null
                    && request.minPrice().compareTo(request.maxPrice()) > 0))
            throw new AppException(ErrorCode.INVALID_REQUEST);
        var names = request.amenityNames() == null ? List.<String>of() : request.amenityNames().stream()
                .filter(name -> name != null && !name.isBlank()).map(name -> name.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct().toList();
        LocalDateTime time = now();
        return page(repository.searchPublished(time, blankToNull(request.roomTypeName()),
                blankToNull(request.provinceId()), blankToNull(request.wardId()), request.rentalStatus(),
                request.minPrice(), request.maxPrice(), names.isEmpty() ? List.of("") : names, names.size(),
                PageRequest.of(page, size)), time);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    @Override
    public PageResponse<ListingResponse> getAllForAdmin(int page, int size, ListingStatus status) {
        var pageable = pageable(page, size);
        LocalDateTime time = now();
        return page(repository.findForAdmin(status == null ? null : status.name(), time, pageable), time);
    }

    @Override
    public ListingResponse getPublishedById(String id) {
        var listing = repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
        if (!published(listing, now())) {
            throw new AppException(ErrorCode.LISTING_NOT_FOUND);
        }
        return response(listing);
    }

    @Override
    public PageResponse<ListingResponse> getMine(String landlordId, int page, int size) {
        return page(repository.findByProperty_Landlord_Id(landlordId, pageable(page, size)), now());
    }

    @Override
    public PageResponse<ListingResponse> getPending(int page, int size) {
        LocalDateTime time = now();
        return page(repository.findByStatusAndPendingExpiresAtGreaterThan(ListingStatus.PENDING, time, pageable(page, size)), time);
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private PageResponse<ListingResponse> page(Page<Listing> result, LocalDateTime time) {
        return PageResponse.<ListingResponse>builder().currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(responses(result.getContent(), time)).build();
    }

    private ListingResponse response(Listing listing) {
        return responses(List.of(listing), now()).getFirst();
    }

    private Map<String, ServiceSubscriptionPeriod> effectivePeriods(List<Listing> listings, LocalDateTime time) {
        Map<String, ServiceSubscriptionPeriod> result = new HashMap<>();
        var owners = listings.stream().map(l -> l.getProperty().getLandlord().getId()).distinct().toList();
        if (!owners.isEmpty()) {
            periods.findEffectivePeriods(owners, time).forEach(p -> result.put(p.getSubscription().getLandlord().getId(), p));
        }
        return result;
    }

    private int selected(Listing listing, ServiceSubscriptionPeriod period, LocalDateTime time) {
        return published(listing, time) && period != null && listing.getServiceSubscriptionPeriod() != null
                && period.getId().equals(listing.getServiceSubscriptionPeriod().getId()) ? listing.getPackagePriorityLevel() : 0;
    }

    private List<ListingResponse> responses(List<Listing> listings, LocalDateTime time) {
        if (listings.isEmpty()) {
            return List.of();
        }
        var activeBoosts = new HashMap<String, Integer>();
        boosts.findEffective(listings.stream().map(Listing::getId).toList(), time)
                .forEach(a -> activeBoosts.merge(a.getListing().getId(), a.getPriorityLevel(), Math::max));
        var currentPeriods = effectivePeriods(listings, time);
        var propertyIds = listings.stream().map(l -> l.getProperty().getId()).distinct().toList();
        var allRooms = roomRepository.findAllByProperty_IdIn(propertyIds);
        var roomIds = allRooms.stream().map(com.tamdaptit.rentalmanagement.room.entity.Room::getId).toList();
        var amenitiesByRoom = roomIds.isEmpty() ? Map.<String, List<com.tamdaptit.rentalmanagement.room.entity.RoomAmenity>>of()
                : roomAmenities.findAllWithAmenityByRoomIds(roomIds).stream()
                    .collect(java.util.stream.Collectors.groupingBy(a -> a.getRoom().getId()));
        var imagesByRoom = roomIds.isEmpty() ? Map.<String, List<com.tamdaptit.rentalmanagement.room.entity.RoomImage>>of()
                : roomImages.findAllByRoomIds(roomIds).stream()
                    .collect(java.util.stream.Collectors.groupingBy(i -> i.getRoom().getId()));
        var roomsByProperty = allRooms.stream().collect(java.util.stream.Collectors.groupingBy(r -> r.getProperty().getId()));
        var sharedAmenities = propertyAmenities.findAllWithAmenityByPropertyIds(propertyIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(a -> a.getProperty().getId()));
        var sharedImages = propertyImages.findAllByProperty_IdInOrderBySortOrderAsc(propertyIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(i -> i.getProperty().getId()));
        return listings.stream().map(listing -> {
            var period = currentPeriods.get(listing.getProperty().getLandlord().getId());
            int selected = selected(listing, period, time);
            int priority = !published(listing, time) ? 0 : Math.max(activeBoosts.getOrDefault(listing.getId(), 0),
                    Math.max(selected, period == null ? 0 : period.getDefaultPriorityLevelSnapshot()));
            String propertyId = listing.getProperty().getId();
            var roomResponses = roomsByProperty.getOrDefault(propertyId, List.of()).stream()
                    .sorted(java.util.Comparator.comparing(com.tamdaptit.rentalmanagement.room.entity.Room::getRoomNumber))
                    .map(room -> roomMapper.toResponse(room, amenitiesByRoom.getOrDefault(room.getId(), List.of()),
                            imagesByRoom.getOrDefault(room.getId(), List.of()))).toList();
            return mapper.toResponse(listing, priority, selected, propertyMapper.toResponse(listing.getProperty(),
                    sharedAmenities.getOrDefault(propertyId, List.of()), sharedImages.getOrDefault(propertyId, List.of()), roomResponses));
        }).toList();
    }

    @Override
    @Transactional
    public void refreshOwner(String landlordId) {
        userRepository.findByIdForUpdate(landlordId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        subscriptions.synchronizeOwner(landlordId);
        LocalDateTime time = now();
        var listings = repository.findByProperty_Landlord_Id(landlordId);
        var currentPeriods = effectivePeriods(listings, time);
        for (var listing : listings) {
            if ((listing.getStatus() == ListingStatus.PENDING && listing.getPendingExpiresAt() != null
                    && !time.isBefore(listing.getPendingExpiresAt()))
                    || (listing.getStatus() == ListingStatus.PUBLISHED && !time.isBefore(listing.getExpiresAt()))) {
                listing.setStatus(ListingStatus.EXPIRED);
            }
            if (selected(listing, currentPeriods.get(landlordId), time) == 0) {
                clearSelection(listing);
            }
        }
        boosts.deleteAll(boosts.findStale(landlordId, time));
        boosts.flush();
        var effective = responses(listings, time);
        for (int i = 0; i < listings.size(); i++) {
            listings.get(i).setPriorityLevel(effective.get(i).priorityLevel());
        }
        repository.flush();
    }
}
