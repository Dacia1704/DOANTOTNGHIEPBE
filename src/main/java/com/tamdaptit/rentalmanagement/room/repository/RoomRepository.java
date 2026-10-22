package com.tamdaptit.rentalmanagement.room.repository;

import com.tamdaptit.rentalmanagement.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    boolean existsByRoomType_Id(String roomTypeId);

    @Query("""
            select r from Room r join fetch r.roomType join fetch r.province join fetch r.ward
            where (:ownerId is null or r.landlord.id = :ownerId)
              and (:typeId is null or r.roomType.id = :typeId)
              and (:provinceId is null or r.province.id = :provinceId)
              and (:wardId is null or r.ward.id = :wardId)
              and (:status is null or r.rentalStatus = :status)
              and (:minPrice is null or r.price >= :minPrice)
              and (:maxPrice is null or r.price <= :maxPrice)
            """)
    Page<Room> search(@Param("ownerId") String ownerId, @Param("typeId") String typeId,
            @Param("provinceId") String provinceId, @Param("wardId") String wardId,
            @Param("status") RoomRentalStatus status, @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice, Pageable pageable);

    @Query("select r from Room r join fetch r.roomType join fetch r.province join fetch r.ward where r.id = :id")
    java.util.Optional<Room> findDetailedById(@Param("id") String id);

    @Query("""
            select r from Room r
            join fetch r.province p
            join fetch r.ward w
            where r.roomType.id = :roomTypeId
            order by r.createdAt desc
            """)
    List<Room> findAllForRoomType(@Param("roomTypeId") String roomTypeId);

    @Query("select r from Room r join fetch r.province join fetch r.ward")
    List<Room> findAllForCategories();
}
