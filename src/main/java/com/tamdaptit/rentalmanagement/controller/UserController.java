package com.tamdaptit.rentalmanagement.controller;

import com.tamdaptit.rentalmanagement.common.*;
import com.tamdaptit.rentalmanagement.dto.user.*;
import com.tamdaptit.rentalmanagement.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Hồ sơ cá nhân và quản trị người dùng")
@PreAuthorize("isAuthenticated()")
public class UserController {
    private final UserService service;

    @GetMapping({"/me", "/get/me"})
    public ApiResponse<UserResponse> me(Principal principal) { return ApiResponse.success(service.get(principal.getName())); }

    @RequestMapping(path = {"/me", "/update/me"}, method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ApiResponse<UserResponse> updateMe(Principal principal, @RequestBody @Valid UpdateProfileRequest request) {
        return ApiResponse.success(service.updateProfile(principal.getName(), request));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(Principal principal, @RequestBody @Valid ChangePasswordRequest request) {
        service.changePassword(principal.getName(), request);
        return ApiResponse.success("Đổi mật khẩu thành công. Vui lòng đăng nhập lại.", null);
    }

    @RequestMapping(path = "/me/avatar", method = {RequestMethod.PUT, RequestMethod.POST}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponse> avatar(Principal principal, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(service.updateAvatar(principal.getName(), file));
    }

    @DeleteMapping("/me/avatar")
    public ApiResponse<UserResponse> deleteAvatar(Principal principal) { return ApiResponse.success(service.deleteAvatar(principal.getName())); }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(q, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> get(@PathVariable String id) { return ApiResponse.success(service.get(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> create(@RequestBody @Valid CreateUserRequest request) {
        return ApiResponse.<UserResponse>builder().code(201).message("Created").data(service.create(request)).build();
    }

    @RequestMapping(path = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> update(@PathVariable String id, @RequestBody @Valid UpdateUserRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.success("Deleted", null);
    }
}
