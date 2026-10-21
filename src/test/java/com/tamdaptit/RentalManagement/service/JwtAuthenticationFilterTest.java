package com.tamdaptit.RentalManagement.service;

import com.tamdaptit.rentalmanagement.entity.User;
import com.tamdaptit.rentalmanagement.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.repository.UserRepository;
import com.tamdaptit.rentalmanagement.security.JwtAuthenticationFilter;
import com.tamdaptit.rentalmanagement.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    JwtTokenService jwt = mock(JwtTokenService.class);
    UserRepository users = mock(UserRepository.class);
    Claims claims = mock(Claims.class);
    User user = User.builder().id("u1").status(UserStatus.ACTIVE).authVersion(1).build();
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        when(jwt.validateToken("token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("u1");
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.get("authVersion")).thenReturn(1);
        when(users.findByIdWithRolesAndPermissions("u1")).thenReturn(Optional.of(user));
        when(jwt.buildAuthorities(user)).thenReturn(List.of("ROLE_TENANT"));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    void runFilter() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
    @Test void currentAccessTokenAuthenticatesWithCurrentDbPermissions() throws Exception {
        runFilter();
        assertEquals("u1", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(jwt, never()).getAuthorities(any());
    }
    @Test void passwordChangeInvalidatesPreviousAccessTokens() throws Exception {
        when(claims.get("authVersion")).thenReturn(0);
        runFilter();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
        when(claims.get("type", String.class)).thenReturn("refresh");
        runFilter();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);
    }
    @Test void blockedOrDeletedUserCannotUseOldToken() throws Exception {
        user.setStatus(UserStatus.BLOCKED);
        runFilter();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        when(users.findByIdWithRolesAndPermissions("u1")).thenReturn(Optional.empty());
        runFilter();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
