package com.tamdaptit.rentalmanagement.service.impl;

import com.tamdaptit.rentalmanagement.entity.Permission;
import com.tamdaptit.rentalmanagement.entity.Role;
import com.tamdaptit.rentalmanagement.entity.RolePermission;
import com.tamdaptit.rentalmanagement.entity.User;
import com.tamdaptit.rentalmanagement.entity.UserRole;
import com.tamdaptit.rentalmanagement.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_JTI = "jti";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.issuer:rental-management}")
    private String issuer;

    @Value("${app.jwt.access-expiration-ms}")
    private Long accessExpiration;

    @Value("${app.jwt.refresh-expiration-ms}")
    private Long refreshExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HMAC-SHA512 yeu cau key >= 64 byte
        if (keyBytes.length < 64) {
            byte[] padded = new byte[64];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            // lap lai de dam bao do phuc tap
            for (int i = keyBytes.length; i < 64; i++) {
                padded[i] = (byte) keyBytes[i % keyBytes.length];
            }
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessExpiration);

        List<String> authorities = buildAuthorities(user);

        return Jwts.builder()
                .subject(user.getId())                       // UUID string
                .claim(CLAIM_SCOPE, String.join(" ", authorities))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_JTI, UUID.randomUUID().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(user.getId())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_JTI, UUID.randomUUID().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public List<String> getAuthorities(Claims claims) {
        String scope = claims.get(CLAIM_SCOPE, String.class);
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return List.of(scope.split(" "));
    }

    /**
     * Build danh sach authorities tu User -> UserRole -> Role -> RolePermission -> Permission.
     * - V moi role: them "ROLE_" + roleCode
     * - V moi permission cua role: them permission.code
     * Dung LinkedHashSet de loai bo trung lap va giu thu tu on dinh.
     */
    @Override
    public List<String> buildAuthorities(User user) {
        if (user == null || user.getUserRoles() == null || user.getUserRoles().isEmpty()) {
            return List.of();
        }

        Set<String> authorities = new LinkedHashSet<>();

        for (UserRole userRole : user.getUserRoles()) {
            Role role = userRole.getRole();
            if (role == null || role.getCode() == null) {
                continue;
            }

            // Ten vai tro: ROLE_TENANT, ROLE_LANDLORD, ROLE_ADMIN
            authorities.add("ROLE_" + role.getCode().name());

            // Quyen cua vai tro
            Set<RolePermission> rolePermissions = role.getRolePermissions();
            if (rolePermissions == null) {
                continue;
            }
            for (RolePermission rolePermission : rolePermissions) {
                Permission permission = rolePermission.getPermission();
                if (permission != null && permission.getCode() != null) {
                    authorities.add(permission.getCode());
                }
            }
        }

        return new ArrayList<>(authorities);
    }

    @Override
    public String getUserId(String token) {
        Claims claims = validateToken(token);
        return claims.getSubject();
    }

    @Override
    public LocalDateTime getAccessTokenExpiresAt() {
        Instant expiresAt = Instant.now().plusMillis(accessExpiration);
        return LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());
    }

    @Override
    public boolean isAccessToken(String token) {
        try {
            return TYPE_ACCESS.equals(validateToken(token).get(CLAIM_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isRefreshToken(String token) {
        try {
            return TYPE_REFRESH.equals(validateToken(token).get(CLAIM_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }
}