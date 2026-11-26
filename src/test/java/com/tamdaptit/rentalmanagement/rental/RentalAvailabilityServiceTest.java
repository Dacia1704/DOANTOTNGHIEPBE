package com.tamdaptit.rentalmanagement.rental;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.rental.repository.RentalRequestRepository;
import com.tamdaptit.rentalmanagement.rental.service.impl.RentalAvailabilityService;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RentalAvailabilityServiceTest {
    private final RentalAgreementRepository agreements = mock(RentalAgreementRepository.class);
    private final RentalRequestRepository requests = mock(RentalRequestRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneOffset.UTC);
    private final RentalAvailabilityService service = new RentalAvailabilityService(agreements, requests, clock);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 26, 10, 0);

    @Test
    void pendingRequestHoldsOnlyAvailableRoomsWithoutChangingPhysicalStatus() {
        var available = room("free", RoomRentalStatus.AVAILABLE);
        var maintenance = room("maintenance", RoomRentalStatus.MAINTENANCE);
        var rented = room("rented", RoomRentalStatus.RENTED);
        var ids = List.of("free", "maintenance", "rented");
        when(requests.pendingRoomIds(ids, now)).thenReturn(ids);

        service.enrich(List.of(available, maintenance, rented));

        assertThat(available.getBookingAvailability()).isEqualTo("PENDING_APPROVAL");
        assertThat(available.getRentalStatus()).isEqualTo(RoomRentalStatus.AVAILABLE);
        assertThat(maintenance.getBookingAvailability()).isEqualTo("MAINTENANCE");
        assertThat(rented.getBookingAvailability()).isEqualTo("RENTED");
        verify(requests).pendingRoomIds(ids, now); // Business time is UTC+7 even with a UTC clock.
    }

    @Test
    void agreementsTakePrecedenceOverLegacyPendingRequests() {
        var ids = List.of("held", "active");
        when(requests.pendingRoomIds(ids, now)).thenReturn(ids);
        var occupancies = List.of(
            occupancy("held", RentalAgreementStatus.PENDING_CONFIRMATION),
            occupancy("active", RentalAgreementStatus.ACTIVE)
        );
        when(agreements.occupancy(ids, RentalAgreementRepository.OCCUPIED)).thenReturn(occupancies);

        var result = service.availability(
            List.of(
                room("held", RoomRentalStatus.AVAILABLE),
                room("active", RoomRentalStatus.AVAILABLE)
            )
        );

        assertThat(result).containsEntry("held", "HELD").containsEntry("active", "RENTED");
    }

    @Test
    void removingPendingHoldMakesTheRoomBookableOnNextRead() {
        var room = room("room", RoomRentalStatus.AVAILABLE);
        when(requests.pendingRoomIds(List.of("room"), now)).thenReturn(List.of("room"), List.of());
        service.enrich(List.of(room));
        assertThat(room.getBookingAvailability()).isEqualTo("PENDING_APPROVAL");

        service.enrich(List.of(room));

        assertThat(room.getBookingAvailability()).isEqualTo("AVAILABLE");
    }

    @Test
    void emptyRoomListDoesNotQueryOccupancy() {
        assertThat(service.availability(List.of())).isEmpty();
        verifyNoInteractions(requests, agreements);
    }

    private Room room(String id, RoomRentalStatus status) {
        return Room.builder().id(id).rentalStatus(status).build();
    }

    private RentalAgreementRepository.Occupancy occupancy(String id, RentalAgreementStatus status) {
        var occupancy = mock(RentalAgreementRepository.Occupancy.class);
        when(occupancy.getRoomId()).thenReturn(id);
        when(occupancy.getStatus()).thenReturn(status);
        return occupancy;
    }
}
