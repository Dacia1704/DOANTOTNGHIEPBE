package com.tamdaptit.rentalmanagement.identity.repository;

import java.util.List;
import java.util.Optional;

import com.tamdaptit.rentalmanagement.identity.entity.Role;
import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.id=:id")
    Optional<Role> lockPermissions(@Param("id") String id);

    Optional<Role> findByCode(RoleCode code);

    List<Role> findAll();

    @Query("""
            SELECT DISTINCT r
            FROM Role r
            LEFT JOIN FETCH r.rolePermissions rp
            LEFT JOIN FETCH rp.permission p
            WHERE r.id = :id
            """)
    Optional<Role> findByIdWithPermissions(@Param("id") String id);
}
