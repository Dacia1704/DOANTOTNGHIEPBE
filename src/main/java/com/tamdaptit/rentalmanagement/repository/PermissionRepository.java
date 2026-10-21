package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.Permission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {

    List<Permission> findAll();

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);
}
