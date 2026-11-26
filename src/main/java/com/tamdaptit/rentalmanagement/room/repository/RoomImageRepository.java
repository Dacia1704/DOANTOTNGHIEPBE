package com.tamdaptit.rentalmanagement.room.repository;

import java.util.Collection;
import java.util.List;

import com.tamdaptit.rentalmanagement.room.entity.RoomImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomImageRepository extends JpaRepository<RoomImage, String> {
    List<RoomImage> findAllByRoom_IdOrderBySortOrderAsc(String roomId);

    @Query("select i from RoomImage i where i.room.id in :roomIds order by i.sortOrder asc")
    List<RoomImage> findAllByRoomIds(@Param("roomIds") Collection<String> roomIds);

    void deleteAllByRoom_Id(String roomId);
}
