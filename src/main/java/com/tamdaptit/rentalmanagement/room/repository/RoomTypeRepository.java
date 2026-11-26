package com.tamdaptit.rentalmanagement.room.repository;

import java.util.Optional;

import com.tamdaptit.rentalmanagement.room.entity.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, String> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByParent_Id(String parentId);

    Page<RoomType> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Optional<RoomType> findByNameIgnoreCase(String name);

    Page<RoomType> findByNameContainingIgnoreCaseAndParent_Id(
        String name,
        String parentId,
        Pageable pageable
    );

    Page<RoomType> findByParent_Id(String parentId, Pageable pageable);
}
