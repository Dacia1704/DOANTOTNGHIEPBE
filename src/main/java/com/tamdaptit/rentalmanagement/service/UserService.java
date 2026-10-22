package com.tamdaptit.rentalmanagement.service;

import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.dto.user.*;
import com.tamdaptit.rentalmanagement.entity.enums.UserStatus;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    UserResponse get(String id);

    PageResponse<UserResponse> list(String q, UserStatus status, int page, int size);

    UserResponse create(CreateUserRequest request);

    UserResponse updateProfile(String id, UpdateProfileRequest request);

    UserResponse update(String id, UpdateUserRequest request);

    void changePassword(String id, ChangePasswordRequest request);

    UserResponse updateAvatar(String id, MultipartFile file);

    UserResponse deleteAvatar(String id);

    void delete(String id);

}
