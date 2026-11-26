package com.tamdaptit.rentalmanagement.rental;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamdaptit.rentalmanagement.address.entity.Province;
import com.tamdaptit.rentalmanagement.address.entity.Ward;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.property.entity.Property;
import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;
import com.tamdaptit.rentalmanagement.property.repository.PropertyImageRepository;
import com.tamdaptit.rentalmanagement.rental.dto.request.RentalRequestCreateRequest;
import com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement;
import com.tamdaptit.rentalmanagement.rental.entity.RentalRequest;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.rental.repository.RentalRequestRepository;
import com.tamdaptit.rentalmanagement.rental.service.impl.RentalAvailabilityService;
import com.tamdaptit.rentalmanagement.rental.service.impl.RentalEvents;
import com.tamdaptit.rentalmanagement.rental.service.impl.RentalServiceImpl;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.entity.RoomType;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RentalRequestHoldTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final RentalRequestRepository requests = mock(RentalRequestRepository.class);
    private final RentalAgreementRepository agreements = mock(RentalAgreementRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PropertyImageRepository images = mock(PropertyImageRepository.class);
    private final RentalEvents events = mock(RentalEvents.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 26, 10, 0);
    private final RentalServiceImpl service = new RentalServiceImpl(
        rooms,
        listings,
        requests,
        agreements,
        users,
        images,
        new RentalAvailabilityService(agreements, requests, clock),
        events,
        new ObjectMapper(),
        clock
    );
    private final User owner = user("owner");
    private final User tenant = user("tenant");
    private final Property property = new Property();
    private final Room room = Room.builder()
        .id("room")
        .roomNumber("101")
        .maxOccupants(2)
        .areaM2(new BigDecimal("20"))
        .price(new BigDecimal("2000000"))
        .roomType(RoomType.builder().name("Phòng trọ").build())
        .build();
    private final Listing listing = new Listing();
    private final RentalRequestCreateRequest body = new RentalRequestCreateRequest(
        "listing",
        "room",
        "Khách thuê",
        "0901234567",
        "tenant@example.com",
        "012345678901",
        "Địa chỉ thường trú",
        LocalDate.of(2026, 11, 27),
        12,
        1,
        "Xin thuê phòng",
        true
    );

    @BeforeEach
    void setup() {
        property.setId("property");
        property.setName("Cơ sở trọ");
        property.setLandlord(owner);
        property.setRentalMode(PropertyRentalMode.BY_ROOM);
        property.setAddressLine("123 Đường A");
        property.setWard(new Ward());
        property.getWard().setName("Phường A");
        property.setProvince(new Province());
        property.getProvince().setName("Thành phố A");
        room.setProperty(property);
        listing.setId("listing");
        listing.setProperty(property);
        listing.setStatus(ListingStatus.PUBLISHED);
        listing.setExpiresAt(now.plusDays(30));
        when(users.findById("tenant")).thenReturn(Optional.of(tenant));
        when(rooms.findByIdForUpdate("room")).thenReturn(Optional.of(room));
        when(listings.findById("listing")).thenReturn(Optional.of(listing));
    }

    @Test
    void anotherTenantCannotBookWhileRequestAwaitsApproval() {
        when(requests.pendingForRoom("room")).thenReturn(List.of(pending("other", now.plusDays(1))));

        assertError(ErrorCode.ROOM_UNAVAILABLE, () -> service.create("tenant", body));

        var order = inOrder(rooms, requests);
        order.verify(rooms).findByIdForUpdate("room");
        order.verify(requests).pendingForRoom("room");
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void sameTenantCanReplayIdenticalRequest() {
        var existing = pending("tenant", now.plusDays(1));
        when(requests.pendingForRoom("room")).thenReturn(List.of(existing));

        assertThat(service.create("tenant", body).id()).isEqualTo("existing");

        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void sameTenantCannotChangePendingRequestByResubmitting() {
        var existing = pending("tenant", now.plusDays(1));
        existing.setMessage("Nội dung khác");
        when(requests.pendingForRoom("room")).thenReturn(List.of(existing));

        assertError(ErrorCode.REQUEST_ALREADY_PENDING, () -> service.create("tenant", body));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void expiredRequestIsReleasedBeforeReplacementIsCreated() {
        var expired = pending("other", now); // The exact expiry boundary no longer holds the room.
        when(requests.pendingForRoom("room")).thenReturn(List.of(expired));
        when(requests.saveAndFlush(any(RentalRequest.class))).thenAnswer(invocation -> {
            RentalRequest created = invocation.getArgument(0);
            created.setId("replacement");
            return created;
        });

        var result = service.create("tenant", body);

        assertThat(result.id()).isEqualTo("replacement");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(expired.getStatus()).isEqualTo(RentalRequestStatus.EXPIRED);
        var order = inOrder(requests);
        order.verify(requests).pendingForRoom("room");
        order.verify(requests).flush();
        order.verify(requests).saveAndFlush(any(RentalRequest.class));
    }

    @Test
    void firstRequestPreventsNextTenantFromBookingAfterRoomLockIsReleased() {
        var pending = new ArrayList<RentalRequest>();
        when(requests.pendingForRoom("room")).thenAnswer(invocation -> List.copyOf(pending));
        when(requests.saveAndFlush(any(RentalRequest.class))).thenAnswer(invocation -> {
            RentalRequest created = invocation.getArgument(0);
            created.setId("first");
            pending.add(created);
            return created;
        });
        when(users.findById("second")).thenReturn(Optional.of(user("second")));

        assertThat(service.create("tenant", body).id()).isEqualTo("first");
        assertError(ErrorCode.ROOM_UNAVAILABLE, () -> service.create("second", body));

        verify(requests, times(1)).saveAndFlush(any(RentalRequest.class));
        verify(rooms, times(2)).findByIdForUpdate("room");
    }

    @Test
    void landlordCanAcceptTheRequestThatHoldsTheRoom() {
        var existing = pending("tenant", now.plusDays(1));
        when(users.findById("owner")).thenReturn(Optional.of(owner));
        when(requests.roomId("existing")).thenReturn(Optional.of("room"));
        when(requests.lock("existing")).thenReturn(Optional.of(existing));
        when(requests.pendingForRoom("room")).thenReturn(List.of());

        var result = service.accept("owner", "existing");

        assertThat(existing.getStatus()).isEqualTo(RentalRequestStatus.ACCEPTED);
        assertThat(result.status()).isEqualTo(RentalAgreementStatus.PENDING_CONFIRMATION.name());
        verify(agreements).saveAndFlush(any(RentalAgreement.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void cancellationOrRejectionReleasesPendingRequest(boolean landlord) {
        var existing = pending("tenant", now.plusDays(1));
        when(requests.roomId("existing")).thenReturn(Optional.of("room"));
        when(requests.lock("existing")).thenReturn(Optional.of(existing));

        service.cancelRequest(landlord ? "owner" : "tenant", "existing", landlord, null);

        assertThat(existing.getStatus()).isEqualTo(
            landlord
                ? RentalRequestStatus.REJECTED
                : RentalRequestStatus.CANCELLED
        );
        assertThat(existing.getRespondedAt()).isEqualTo(now);
        verify(rooms).findByIdForUpdate("room");
    }

    @Test
    void expiryJobReleasesRequestAtTheExpiryBoundary() {
        var existing = pending("tenant", now);
        when(requests.roomId("existing")).thenReturn(Optional.of("room"));
        when(requests.lock("existing")).thenReturn(Optional.of(existing));

        service.expireRequest("existing");

        assertThat(existing.getStatus()).isEqualTo(RentalRequestStatus.EXPIRED);
        verify(rooms).findByIdForUpdate("room");
    }

    private RentalRequest pending(String tenantId, LocalDateTime expiry) {
        return RentalRequest.builder()
            .id("existing")
            .listing(listing)
            .room(room)
            .tenant(user(tenantId))
            .landlord(owner)
            .tenantFullName(body.tenantFullName())
            .tenantPhone(body.tenantPhone())
            .tenantEmail(body.tenantEmail())
            .tenantCitizenId(body.tenantCitizenId())
            .tenantPermanentAddress(body.tenantPermanentAddress())
            .proposedMoveInDate(body.proposedMoveInDate())
            .durationMonths(body.durationMonths())
            .occupantCount(body.occupantCount())
            .message(body.message())
            .expiresAt(expiry)
            .build();
    }

    private static User user(String id) {
        var user = new User();
        user.setId(id);
        user.setFullName("Khách " + id);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private void assertError(ErrorCode code, ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(
            AppException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code)
        );
    }
}
