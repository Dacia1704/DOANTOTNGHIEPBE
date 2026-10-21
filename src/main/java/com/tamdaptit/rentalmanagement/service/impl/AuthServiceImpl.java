package com.tamdaptit.rentalmanagement.service.impl;

import com.tamdaptit.rentalmanagement.dto.auth.request.LoginRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.LogoutRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.RefreshTokenRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.RegisterRequest;
import com.tamdaptit.rentalmanagement.dto.auth.response.LoginResponse;
import com.tamdaptit.rentalmanagement.dto.auth.response.RefreshTokenResponse;
import com.tamdaptit.rentalmanagement.dto.auth.response.RegisterResponse;
import com.tamdaptit.rentalmanagement.entity.RefreshToken;
import com.tamdaptit.rentalmanagement.entity.Role;
import com.tamdaptit.rentalmanagement.entity.User;
import com.tamdaptit.rentalmanagement.entity.UserRole;
import com.tamdaptit.rentalmanagement.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.exception.AppException;
import com.tamdaptit.rentalmanagement.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.mapper.AuthMapper;
import com.tamdaptit.rentalmanagement.mapper.UserMapper;
import com.tamdaptit.rentalmanagement.repository.RefreshTokenRepository;
import com.tamdaptit.rentalmanagement.repository.RoleRepository;
import com.tamdaptit.rentalmanagement.repository.UserRepository;
import com.tamdaptit.rentalmanagement.repository.UserRoleRepository;
import com.tamdaptit.rentalmanagement.service.AuthService;
import com.tamdaptit.rentalmanagement.service.JwtTokenService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthServiceImpl implements AuthService {

    final UserRepository userRepository;
    final RoleRepository roleRepository;
    final UserRoleRepository userRoleRepository;
    final RefreshTokenRepository refreshTokenRepository;

    final PasswordEncoder passwordEncoder;

    final UserMapper userMapper;
    final AuthMapper authMapper;
    final JwtTokenService jwtTokenService;

    @Value("${app.jwt.refresh-expiration-ms}")
    Long refreshExpiration;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        // 1. Validate email chua ton tai
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    String.format("Email %s đã tồn tại", request.getEmail())
            );
        }

        // 2. Validate so dien thoai chua ton tai
        if (request.getPhone() != null
                && !request.getPhone().isBlank()
                && userRepository.existsByPhone(request.getPhone())) {

            throw new AppException(
                    ErrorCode.PHONE_ALREADY_EXISTS,
                    String.format("Số điện thoại %s đã tồn tại", request.getPhone())
            );
        }

        // 3. Validate CCCD chua ton tai (neu co)
        if (request.getCitizenId() != null
                && !request.getCitizenId().isBlank()
                && userRepository.existsByCitizenId(request.getCitizenId())) {

            throw new AppException(
                    ErrorCode.CITIZEN_ID_ALREADY_EXISTS,
                    String.format("Số CCCD/CMND %s đã tồn tại", request.getCitizenId())
            );
        }

        // 4. Chi cho phep dang ky voi TENANT hoac LANDLORD (khong cho ADMIN)
        RoleCode roleCode = request.getRoleCode();
        if (roleCode != RoleCode.TENANT && roleCode != RoleCode.LANDLORD) {
            throw new AppException(
                    ErrorCode.INVALID_REGISTER_ROLE,
                    "Không thể đăng ký tài khoản với vai trò quản trị viên"
            );
        }

        // 5. Tim role trong DB
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new AppException(
                        ErrorCode.ROLE_NOT_FOUND,
                        String.format("Không tìm thấy vai trò %s", roleCode)
                ));

        // 6. Tao user (mapper chuyen DTO -> entity, chua set password)
        User user = userMapper.toUser(request);

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerifiedAt(null);
        user.setPhoneVerifiedAt(null);

        User savedUser = userRepository.save(user);

        // 7. Gan role qua bang trung gian user_roles
        UserRole userRole = UserRole.builder()
                .user(savedUser)
                .role(role)
                .build();
        savedUser.getUserRoles().add(userRole);
        userRoleRepository.save(userRole);

        // 8. Nap lai user kem roles + permissions de build authorities
        User userWithRoles = userRepository
                .findByIdWithRolesAndPermissions(savedUser.getId())
                .orElse(savedUser);

        // 9. Sinh token
        List<String> authorities = jwtTokenService.buildAuthorities(userWithRoles);
        String accessToken = jwtTokenService.generateAccessToken(userWithRoles);
        String rawRefreshToken = jwtTokenService.generateRefreshToken(userWithRoles);

        // 10. Luu refresh token
        saveRefreshToken(savedUser, rawRefreshToken);

        return authMapper.toRegisterResponse(userWithRoles, authorities, accessToken, rawRefreshToken);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {

        // 1. Tim user theo email (kem roles + permissions)
        User user = userRepository
                .findByEmailWithRolesAndPermissions(request.getEmail())
                .orElseThrow(() -> new AppException(
                        ErrorCode.INVALID_CREDENTIALS,
                        "Email hoặc mật khẩu không đúng"
                ));

        // 2. Kiem tra trang thai tai khoan
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.USER_BLOCKED);
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        // 3. Kiem tra mat khau
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new AppException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không đúng"
            );
        }

        // 4. Sinh token
        List<String> authorities = jwtTokenService.buildAuthorities(user);
        String accessToken = jwtTokenService.generateAccessToken(user);
        String rawRefreshToken = jwtTokenService.generateRefreshToken(user);

        // 5. Luu refresh token (login la read-only nen ghi rieng)
        saveRefreshToken(user, rawRefreshToken);

        return authMapper.toLoginResponse(
                user,
                authorities,
                accessToken,
                rawRefreshToken,
                jwtTokenService.getAccessTokenExpiresAt()
        );
    }

    @Override
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {

        // 1. Tim refresh token trong DB
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 2. Kiem tra het han
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new AppException(ErrorCode.TOKEN_EXPIRED, "Refresh token đã hết hạn");
        }

        // 3. Nap user kem roles + permissions
        User user = userRepository
                .findByIdWithRolesAndPermissions(refreshToken.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.USER_BLOCKED);
        }

        // 4. Revoke token cu (rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // 5. Cap cap moi token
        String accessToken = jwtTokenService.generateAccessToken(user);
        String rawRefreshToken = jwtTokenService.generateRefreshToken(user);

        saveRefreshToken(user, rawRefreshToken);

        return authMapper.toRefreshTokenResponse(
                accessToken,
                rawRefreshToken,
                jwtTokenService.getAccessTokenExpiresAt()
        );
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!refreshToken.getUser().getId().equals(userIdFromSecurity())) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    private void saveRefreshToken(User user, String rawRefreshToken) {
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .user(user)
                        .token(rawRefreshToken)
                        .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpiration)))
                        .revoked(false)
                        .build()
        );
    }

    private String userIdFromSecurity() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return authentication.getName();
    }
}
