package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.Room;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
