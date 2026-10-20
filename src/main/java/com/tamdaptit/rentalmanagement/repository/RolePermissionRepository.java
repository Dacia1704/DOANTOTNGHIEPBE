package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.RolePermission;
import com.tamdaptit.rentalmanagement.entity.RolePermissionId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findAllByIdRoleId(String roleId);

    Optional<RolePermission> findByIdRoleIdAndIdPermissionId(String roleId, String permissionId);
}