package com.tamdaptit.rentalmanagement.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.security.Principal;
import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.identity.dto.request.ChangePasswordRequest;
import com.tamdaptit.rentalmanagement.identity.dto.request.CreateUserRequest;
import com.tamdaptit.rentalmanagement.identity.dto.request.UpdateProfileRequest;
import com.tamdaptit.rentalmanagement.identity.dto.request.UpdateUserRequest;
import com.tamdaptit.rentalmanagement.identity.dto.response.UserResponse;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public ApiResponse<UserResponse> updateAvatar(Principal principal, @RequestParam("file") MultipartFile file) {
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
