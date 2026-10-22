package com.tamdaptit.rentalmanagement.room.repository;

import com.tamdaptit.rentalmanagement.room.entity.RoomAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface RoomAmenityRepository extends JpaRepository<RoomAmenity, String> {

    /**
     * Kiem tra tien ich dang duoc gan cho it nhat mot phong.
     * Dung de chan viec xoa amenity dang duoc su dung thay vi de lai loi FK tho.
     */
    boolean existsByAmenity_Id(String amenityId);

    List<RoomAmenity> findAllByRoom_Id(String roomId);

    @Query("select ra from RoomAmenity ra join fetch ra.amenity where ra.room.id in :roomIds")
    List<RoomAmenity> findAllWithAmenityByRoomIds(@Param("roomIds") Collection<String> roomIds);
}
