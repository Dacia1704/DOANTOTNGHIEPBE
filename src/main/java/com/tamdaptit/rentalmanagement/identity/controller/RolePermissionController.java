package com.tamdaptit.rentalmanagement.identity.controller;
import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.identity.service.RolePermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class RolePermissionController {
    private final RolePermissionService service;
    @GetMapping("/users/me/authorities")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<String>> authorities(Principal actor) {
        return ApiResponse.success(service.authorities(actor.getName()));
    }
    @GetMapping("/admin/role-permissions")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ROLE_PERMISSION_READ')")
    public ApiResponse<RolePermissionService.Matrix> matrix(Principal actor) {
        return ApiResponse.success(service.matrix(actor.getName()));
    }
    @PatchMapping("/admin/roles/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ROLE_PERMISSION_MANAGE')")
    public ApiResponse<RolePermissionService.ChangeResult> change(Principal actor,@PathVariable String roleId,
        @PathVariable String permissionId,@Valid @RequestBody RolePermissionService.Change body) {
        return ApiResponse.success(service.change(actor.getName(),roleId,permissionId,body));
    }
    @GetMapping("/admin/role-permissions/audit")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ROLE_PERMISSION_READ')")
    public ApiResponse<List<Map<String,Object>>> audit(Principal actor,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.audit(actor.getName(),page,size));
    }
}
