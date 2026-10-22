package com.tamdaptit.rentalmanagement.identity.repository;

import com.tamdaptit.rentalmanagement.identity.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, String> {

    List<RolePermission> findAllByRole_Id(String roleId);

    Optional<RolePermission> findByRole_IdAndPermission_Id(String roleId, String permissionId);
}
