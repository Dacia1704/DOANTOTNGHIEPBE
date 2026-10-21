package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, String> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByParent_Id(String parentId);
}
