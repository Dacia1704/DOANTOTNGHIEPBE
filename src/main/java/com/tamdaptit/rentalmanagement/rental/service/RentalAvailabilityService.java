package com.tamdaptit.rentalmanagement.rental.service;
import com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.room.entity.Room;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.tamdaptit.rentalmanagement.rental.repository.RentalAgreementRepository.OCCUPIED;

@Service @RequiredArgsConstructor
public class RentalAvailabilityService {
    private final RentalAgreementRepository agreements;
    public void enrich(List<Room> rooms) {
        var states=availability(rooms);
        rooms.forEach(r->r.setBookingAvailability(states.get(r.getId())));
    }
    public Map<String,String> availability(List<Room> rooms) {
        Map<String,String> result=new HashMap<>();
        rooms.forEach(r->result.put(r.getId(),r.getRentalStatus().name()));
        if(!rooms.isEmpty()) agreements.occupancy(rooms.stream().map(Room::getId).toList(),OCCUPIED)
            .forEach(a->result.put(a.getRoomId(),a.getStatus()==RentalAgreementStatus.ACTIVE ? "RENTED" : "HELD"));
        return result;
    }
    // Only call after locking the room row for writes.
    public void requireFree(Room room) {
        if(room.getRentalStatus()!=RoomRentalStatus.AVAILABLE || agreements.existsByRoom_IdAndStatusIn(room.getId(),OCCUPIED))
            throw new com.tamdaptit.rentalmanagement.common.exception.AppException(
                com.tamdaptit.rentalmanagement.common.exception.ErrorCode.ROOM_UNAVAILABLE);
    }
}
