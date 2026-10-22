package com.tamdaptit.rentalmanagement.room.repository;

import com.tamdaptit.rentalmanagement.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    boolean existsByRoomType_Id(String roomTypeId);

    @Query("""
            select r from Room r
            join fetch r.province p
            join fetch r.ward w
            where r.roomType.id = :roomTypeId
            order by r.createdAt desc
            """)
    List<Room> findAllForRoomType(@Param("roomTypeId") String roomTypeId);
}
