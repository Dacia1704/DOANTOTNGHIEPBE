package com.tamdaptit.rentalmanagement.identity.service;

import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.entity.enums.*;
import com.tamdaptit.rentalmanagement.identity.repository.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class RolePermissionService {
    public static final Set<String> MANAGED=Set.of("USER_READ","USER_MANAGE","REVIEW_READ","REVIEW_MODERATE",
        "REPORT_READ","REPORT_HANDLE","ROLE_PERMISSION_READ","ROLE_PERMISSION_MANAGE");
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository links;
    private final JwtTokenService tokens;
    private final JdbcTemplate jdbc;

    public List<String> authorities(String actor) {
        var user=users.findByIdWithRolesAndPermissions(actor).orElseThrow(()->new AppException(ErrorCode.USER_NOT_FOUND));
        if(user.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.UNAUTHORIZED);
        return tokens.buildAuthorities(user);
    }
    private void require(String actor,String permission) {
        var user=users.findByIdWithRolesAndPermissions(actor).orElseThrow(()->new AppException(ErrorCode.USER_NOT_FOUND));
        if(user.getStatus()!=UserStatus.ACTIVE || user.getUserRoles().stream().noneMatch(r->r.getRole().getCode()==RoleCode.ADMIN)
            || !tokens.buildAuthorities(user).contains(permission)) throw new AppException(ErrorCode.UNAUTHORIZED);
    }
    public Matrix matrix(String actor) {
        require(actor,"ROLE_PERMISSION_READ");
        var catalog=permissions.findAll().stream().filter(p->MANAGED.contains(p.getCode()))
            .sorted(Comparator.comparing(p->p.getCode())).map(p->new PermissionRow(p.getId(),p.getCode(),p.getName(),p.getCode().split("_")[0])).toList();
        var rows=roles.findAll().stream().sorted(Comparator.comparing(r->r.getCode().name())).map(r->
            new RoleRow(r.getId(),r.getCode().name(),r.getName(),r.getPermissionVersion(),
                links.findAllByRole_Id(r.getId()).stream().map(l->l.getPermission().getId()).filter(id->catalog.stream().anyMatch(p->p.id().equals(id))).toList(),
                catalog.stream().filter(p->p.code().startsWith("ROLE_PERMISSION_")
                    || (p.code().equals("USER_MANAGE") && r.getCode()!=RoleCode.ADMIN)).map(PermissionRow::id).toList())).toList();
        return new Matrix(rows,catalog);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ChangeResult change(String actor,String roleId,String permissionId,Change body) {
        require(actor,"ROLE_PERMISSION_MANAGE");
        var role=roles.lockPermissions(roleId).orElseThrow(()->new AppException(ErrorCode.ROLE_NOT_FOUND));
        var permission=permissions.findById(permissionId).orElseThrow(()->new AppException(ErrorCode.PERMISSION_NOT_FOUND));
        if(!MANAGED.contains(permission.getCode()) || permission.getCode().startsWith("ROLE_PERMISSION_")
            || (permission.getCode().equals("USER_MANAGE") && role.getCode()!=RoleCode.ADMIN))
            throw new AppException(ErrorCode.PERMISSION_PROTECTED);
        if(body.enabled()==null || body.expectedVersion()==null || body.expectedVersion()<0) throw new AppException(ErrorCode.INVALID_REQUEST);
        long current=jdbc.queryForObject("SELECT permission_version FROM roles WHERE id=? FOR UPDATE",Long.class,roleId);
        if(current!=body.expectedVersion()) throw new AppException(ErrorCode.PERMISSION_VERSION_CONFLICT);
        var existing=links.findByRole_IdAndPermission_Id(roleId,permissionId);
        boolean before=existing.isPresent();
        if(before!=body.enabled()) {
            if(body.enabled()) links.save(com.tamdaptit.rentalmanagement.identity.entity.RolePermission.builder().role(role).permission(permission).build());
            else links.delete(existing.orElseThrow());
            role.setPermissionVersion(current+1);
            roles.flush();
            jdbc.update("INSERT INTO permission_audit(id,actor_id,role_id,permission_id,old_enabled,new_enabled,role_version) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),actor,roleId,permissionId,before,body.enabled(),role.getPermissionVersion());
        }
        return new ChangeResult(roleId,permissionId,body.enabled(),before==body.enabled()?current:current+1);
    }
    public List<Map<String,Object>> audit(String actor,int page,int size) {
        require(actor,"ROLE_PERMISSION_READ");
        if(page<0 || size<1 || size>50 || page>Integer.MAX_VALUE/size) throw new AppException(ErrorCode.INVALID_REQUEST);
        return jdbc.queryForList("""
            SELECT a.*,u.full_name AS actor_name,r.code AS role_code,p.code AS permission_code
            FROM permission_audit a LEFT JOIN users u ON u.id=a.actor_id
            LEFT JOIN roles r ON r.id=a.role_id LEFT JOIN permissions p ON p.id=a.permission_id
            ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?
            """,size,page*size);
    }
    public record Change(@NotNull Boolean enabled,@NotNull @Min(0) Long expectedVersion) {}
    public record ChangeResult(String roleId,String permissionId,boolean enabled,long version) {}
    public record PermissionRow(String id,String code,String name,String module) {}
    public record RoleRow(String id,String code,String name,long version,List<String> permissionIds,List<String> lockedPermissionIds) {}
    public record Matrix(List<RoleRow> roles,List<PermissionRow> permissions) {}
}
