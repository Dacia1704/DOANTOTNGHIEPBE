package com.tamdaptit.rentalmanagement.identity.repository;

import java.util.List;
import java.util.Optional;

import com.tamdaptit.rentalmanagement.identity.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    List<Permission> findAll();

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);
}
