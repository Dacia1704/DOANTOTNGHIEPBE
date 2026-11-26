package com.tamdaptit.rentalmanagement.rental.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.rental.repository.RentalRequestRepository;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository.OCCUPIED;

@Service
@RequiredArgsConstructor
public class RentalAvailabilityService {
    private final RentalAgreementRepository agreements;
    private final RentalRequestRepository requests;
    private final Clock clock;

    public void enrich(List<Room> rooms) {
        var states = availability(rooms);
        rooms.forEach(room -> room.setBookingAvailability(states.get(room.getId())));
    }

    public Map<String, String> availability(List<Room> rooms) {
        Map<String, String> result = new HashMap<>();
        rooms.forEach(room -> result.put(room.getId(), room.getRentalStatus().name()));
        if (!rooms.isEmpty()) {
            var ids = rooms.stream().map(Room::getId).toList();
            var now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
            requests.pendingRoomIds(ids, now).forEach(id -> {
                if ("AVAILABLE".equals(result.get(id))) {
                    result.put(id, "PENDING_APPROVAL");
                }
            });
            agreements.occupancy(ids, OCCUPIED)
                .forEach(
                    agreement -> result.put(
                        agreement.getRoomId(),
                        agreement.getStatus() == RentalAgreementStatus.ACTIVE ? "RENTED" : "HELD"
                    )
                );
        }

        return result;
    }

    // Only call after locking the room row for writes.
    public void requireFree(Room room) {
        if (
            room.getRentalStatus() != RoomRentalStatus.AVAILABLE ||
            agreements.existsByRoom_IdAndStatusIn(room.getId(), OCCUPIED)
        ) {
            throw new AppException(ErrorCode.ROOM_UNAVAILABLE);
        }
    }
}
