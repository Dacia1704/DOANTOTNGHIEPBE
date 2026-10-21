package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.RolePermission;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, String> {

    List<RolePermission> findAllByRole_Id(String roleId);

    Optional<RolePermission> findByRole_IdAndPermission_Id(String roleId, String permissionId);
}
