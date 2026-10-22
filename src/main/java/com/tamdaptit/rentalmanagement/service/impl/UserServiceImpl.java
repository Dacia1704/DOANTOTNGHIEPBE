package com.tamdaptit.rentalmanagement.service.impl;

import com.tamdaptit.rentalmanagement.service.*;

import com.tamdaptit.rentalmanagement.common.PageResponse;
import com.tamdaptit.rentalmanagement.dto.user.*;
import com.tamdaptit.rentalmanagement.entity.*;
import com.tamdaptit.rentalmanagement.entity.enums.*;
import com.tamdaptit.rentalmanagement.exception.*;
import com.tamdaptit.rentalmanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AvatarStorage avatars;
    private final UserDeletionGuard deletionGuard;

    @Override
    public UserResponse get(String id) { return UserResponse.from(find(id)); }

    @Override
    public PageResponse<UserResponse> list(String q, UserStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 100)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "page >= 0, size 1..100, q <= 100 characters");
        }
        Specification<User> filter = (root, query, cb) -> cb.conjunction();
        if (status != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (q != null && !q.isBlank()) {
            String term = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_") + "%";
            filter = filter.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), term, '\\'),
                    cb.like(cb.lower(root.get("email")), term, '\\'),
                    cb.like(root.get("phone"), term, '\\')));
        }
        var result = users.findAll(filter, PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
        return PageResponse.<UserResponse>builder().currentPage(page).pageSize(size)
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(UserResponse::from).toList()).build();
    }

    @Transactional
    @Override
    public UserResponse create(CreateUserRequest request) {
        checkPasswordLength(request.password());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        unique(email, request.phone(), request.citizenId(), "");
        var role = roles.findByCode(request.roleCode()).orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        var user = users.saveAndFlush(User.builder().email(email).fullName(request.fullName().trim())
                .phone(optional(request.phone())).citizenId(optional(request.citizenId()))
                .password(encoder.encode(request.password())).status(UserStatus.ACTIVE).build());
        var link = userRoles.save(UserRole.builder().user(user).role(role).build());
        user.getUserRoles().add(link);
        return UserResponse.from(user);
    }

    @Transactional
    @Override
    public UserResponse updateProfile(String id, UpdateProfileRequest request) {
        var user = locked(id);
        profile(user, request.fullName(), request.phone(), request.citizenId());
        return UserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    @Override
    public UserResponse update(String id, UpdateUserRequest request) {
        var user = locked(id);
        if (request.status() != null && request.status() != UserStatus.ACTIVE && isAdmin(user)) {
            throw new AppException(ErrorCode.PROTECTED_ADMIN);
        }
        boolean invalidate = false;
        if (request.email() != null) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            unique(email, null, null, id);
            if (!email.equals(user.getEmail())) {
                user.setEmail(email);
                user.setEmailVerifiedAt(null);
                invalidate = true;
            }
        }
        if (request.status() != null && request.status() != user.getStatus()) {
            user.setStatus(request.status());
            invalidate = true;
        }
        profile(user, request.fullName(), request.phone(), request.citizenId());
        if (invalidate) invalidate(user);
        return UserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    @Override
    public void changePassword(String id, ChangePasswordRequest request) {
        var user = locked(id);
        if (!encoder.matches(request.currentPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }
        checkPasswordLength(request.newPassword());
        if (encoder.matches(request.newPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.PASSWORD_UNCHANGED);
        }
        user.setPassword(encoder.encode(request.newPassword()));
        invalidate(user);
        users.saveAndFlush(user);
    }

    @Transactional
    @Override
    public UserResponse updateAvatar(String id, MultipartFile file) {
        var user = locked(id);
        String oldId = user.getAvatarPublicId();
        var uploaded = avatars.upload(id, file);
        cleanupOnCompletion(oldId, uploaded.publicId());
        user.setAvatarUrl(uploaded.url());
        user.setAvatarPublicId(uploaded.publicId());
        return UserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    @Override
    public UserResponse deleteAvatar(String id) {
        var user = locked(id);
        cleanupOnCompletion(user.getAvatarPublicId(), null);
        user.setAvatarUrl(null);
        user.setAvatarPublicId(null);
        return UserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    @Override
    public void delete(String id) {
        var user = locked(id);
        if (isAdmin(user)) throw new AppException(ErrorCode.PROTECTED_ADMIN);
        if (deletionGuard.hasReferences(id)) throw new AppException(ErrorCode.USER_IN_USE);
        userRoles.deleteAllByUser_Id(id);
        refreshTokens.deleteAllByUser_Id(id);
        cleanupOnCompletion(user.getAvatarPublicId(), null);
        users.delete(user);
        users.flush();
    }

    private void profile(User user, String name, String phone, String citizenId) {
        unique(null, phone, citizenId, user.getId());
        if (name != null) user.setFullName(name.trim());
        if (phone != null && !Objects.equals(optional(phone), user.getPhone())) {
            user.setPhone(optional(phone));
            user.setPhoneVerifiedAt(null);
        }
        if (citizenId != null) user.setCitizenId(optional(citizenId));
    }

    private void unique(String email, String phone, String citizenId, String id) {
        if (email != null && users.existsByEmailIgnoreCaseAndIdNot(email, id)) throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        if (optional(phone) != null && users.existsByPhoneAndIdNot(phone, id)) throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        if (optional(citizenId) != null && users.existsByCitizenIdAndIdNot(citizenId.trim(), id)) throw new AppException(ErrorCode.CITIZEN_ID_ALREADY_EXISTS);
    }

    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private User find(String id) { return users.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)); }
    private User locked(String id) { return users.findByIdForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)); }
    private boolean isAdmin(User user) { return user.getUserRoles().stream().anyMatch(ur -> ur.getRole().getCode() == RoleCode.ADMIN); }
    private void invalidate(User user) {
        user.setAuthVersion(user.getAuthVersion() + 1);
        refreshTokens.revokeAllByUserId(user.getId());
    }
    private void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8");
        }
    }

    private void cleanupOnCompletion(String oldId, String newId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED && status != STATUS_ROLLED_BACK) {
                    log.warn("Avatar transaction outcome unknown; retain both assets for reconciliation");
                    return;
                }
                String id = status == STATUS_COMMITTED ? oldId : newId;
                if (id != null && !id.isBlank()) {
                    try { avatars.delete(id); }
                    catch (RuntimeException ex) { log.warn("Avatar cleanup failed, retry deletion for public_id={}", id); }
                }
            }
        });
    }
}
