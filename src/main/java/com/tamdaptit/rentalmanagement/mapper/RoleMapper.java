package com.tamdaptit.rentalmanagement.mapper;

import com.tamdaptit.rentalmanagement.entity.Permission;
import com.tamdaptit.rentalmanagement.entity.Role;
import com.tamdaptit.rentalmanagement.entity.RolePermission;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    /**
     * Lay danh sach ma quyen cua mot role.
     */
    default List<String> mapPermissionCodes(Set<RolePermission> rolePermissions) {
        if (rolePermissions == null) {
            return List.of();
        }
        return rolePermissions.stream()
                .map(RolePermission::getPermission)
                .filter(Objects::nonNull)
                .map(Permission::getCode)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Lay danh sach permission cua mot role.
     */
    default List<Permission> mapPermissions(Set<RolePermission> rolePermissions) {
        if (rolePermissions == null) {
            return List.of();
        }
        return rolePermissions.stream()
                .map(RolePermission::getPermission)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    default String mapRoleCode(Role role) {
        return role == null || role.getCode() == null ? null : role.getCode().name();
    }

    default String mapRoleName(Role role) {
        return role == null ? null : role.getName();
    }
}