package com.tamdaptit.RentalManagement.service;

import com.tamdaptit.rentalmanagement.dto.user.*;
import com.tamdaptit.rentalmanagement.entity.*;
import com.tamdaptit.rentalmanagement.entity.enums.*;
import com.tamdaptit.rentalmanagement.exception.*;
import com.tamdaptit.rentalmanagement.repository.*;
import com.tamdaptit.rentalmanagement.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final UserRoleRepository links = mock(UserRoleRepository.class);
    final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    final AvatarStorage avatars = mock(AvatarStorage.class);
    final UserDeletionGuard guard = mock(UserDeletionGuard.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final UserService service = new UserService(users, roles, links, tokens, encoder, avatars, guard);
    User user;

    @BeforeEach void setup() {
        user = User.builder().id("u1").email("user@example.com").fullName("User")
                .password(encoder.encode("OldPassword@123")).status(UserStatus.ACTIVE).build();
        when(users.findByIdForUpdate("u1")).thenReturn(Optional.of(user));
        when(users.findById("u1")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void passwordChangeChecksCurrentPasswordBeforeMutation() {
        var ex = assertThrows(AppException.class, () -> service.changePassword("u1",
                new ChangePasswordRequest("wrong", "NewPassword@123", "NewPassword@123")));
        assertEquals(ErrorCode.CURRENT_PASSWORD_INCORRECT, ex.getErrorCode());
        verifyNoInteractions(tokens);
        assertEquals(0, user.getAuthVersion());
        assertTrue(encoder.matches("OldPassword@123", user.getPassword()));
    }

    @Test void rejectsUnconfirmedAndUnchangedPassword() {
        assertEquals(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH,
                assertThrows(AppException.class, () -> service.changePassword("u1",
                        new ChangePasswordRequest("OldPassword@123", "NewPassword@123", "different"))).getErrorCode());
        assertEquals(ErrorCode.PASSWORD_UNCHANGED,
                assertThrows(AppException.class, () -> service.changePassword("u1",
                        new ChangePasswordRequest("OldPassword@123", "OldPassword@123", "OldPassword@123"))).getErrorCode());
        verifyNoInteractions(tokens);
    }

    @Test void changesHashRevokesRefreshTokensAndIncrementsAccessTokenVersion() {
        service.changePassword("u1", new ChangePasswordRequest("OldPassword@123", "NewPassword@123", "NewPassword@123"));
        assertTrue(encoder.matches("NewPassword@123", user.getPassword()));
        assertFalse(encoder.matches("OldPassword@123", user.getPassword()));
        assertEquals(1, user.getAuthVersion());
        verify(tokens).revokeAllByUserId("u1");
    }

    @Test void rejectsPasswordsBeyondBcryptByteLimit() {
        String password = "ấ".repeat(30);
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(AppException.class, () -> service.changePassword("u1",
                new ChangePasswordRequest("OldPassword@123", password, password))).getErrorCode());
        assertEquals(0, user.getAuthVersion());
    }

    @Test void phoneChangeResetsVerificationAndProfileDoesNotChangePrivileges() {
        user.setPhoneVerifiedAt(LocalDateTime.now());
        var response = service.updateProfile("u1", new UpdateProfileRequest("New Name", "0901234567", null));
        assertEquals("New Name", response.fullName());
        assertNull(response.phoneVerifiedAt());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(0, user.getAuthVersion());
        verifyNoInteractions(tokens, roles);
    }

    @Test void duplicatePhoneIsRejected() {
        when(users.existsByPhoneAndIdNot("0901234567", "u1")).thenReturn(true);
        assertEquals(ErrorCode.PHONE_ALREADY_EXISTS, assertThrows(AppException.class,
                () -> service.updateProfile("u1", new UpdateProfileRequest(null, "0901234567", null))).getErrorCode());
    }

    @Test void blockingUserInvalidatesTokensImmediately() {
        service.update("u1", new UpdateUserRequest(null, null, null, null, UserStatus.BLOCKED));
        assertEquals(UserStatus.BLOCKED, user.getStatus());
        assertEquals(1, user.getAuthVersion());
        verify(tokens).revokeAllByUserId("u1");
    }

    @Test void protectsAdminFromDeletionOrDeactivation() {
        user.getUserRoles().add(UserRole.builder().user(user).role(Role.builder().code(RoleCode.ADMIN).build()).build());
        assertEquals(ErrorCode.PROTECTED_ADMIN, assertThrows(AppException.class, () -> service.delete("u1")).getErrorCode());
        assertEquals(ErrorCode.PROTECTED_ADMIN, assertThrows(AppException.class,
                () -> service.update("u1", new UpdateUserRequest(null, null, null, null, UserStatus.INACTIVE))).getErrorCode());
        verify(users, never()).delete(any(User.class));
    }

    @Test void refusesCascadeDeletionOfBusinessData() {
        when(guard.hasReferences("u1")).thenReturn(true);
        assertEquals(ErrorCode.USER_IN_USE, assertThrows(AppException.class, () -> service.delete("u1")).getErrorCode());
        verifyNoInteractions(links, tokens);
    }

    @Test void deletesUnreferencedUserAndAuthenticationMappings() {
        TransactionSynchronizationManager.initSynchronization();
        service.delete("u1");
        verify(links).deleteAllByUser_Id("u1");
        verify(tokens).deleteAllByUser_Id("u1");
        verify(users).delete(user);
    }

    @Test void avatarReplacementDeletesOldImageOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        user.setAvatarPublicId("old-avatar");
        user.setAvatarUrl("https://old.example/avatar.png");
        var file = new MockMultipartFile("file", new byte[]{1});
        when(avatars.upload("u1", file)).thenReturn(new AvatarStorage.UploadedAvatar("https://new.example/a.png", "new-avatar"));
        assertEquals("https://new.example/a.png", service.updateAvatar("u1", file).avatarUrl());
        verify(avatars, never()).delete(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(avatars).delete("old-avatar");
        verify(avatars, never()).delete("new-avatar");
    }

    @Test void dbRollbackDeletesNewUploadAndPreservesOldImage() {
        TransactionSynchronizationManager.initSynchronization();
        user.setAvatarPublicId("old-avatar");
        var file = new MockMultipartFile("file", new byte[]{1});
        when(avatars.upload("u1", file)).thenReturn(new AvatarStorage.UploadedAvatar("https://new.example/a.png", "new-avatar"));
        when(users.saveAndFlush(user)).thenThrow(new IllegalStateException("database failure"));
        assertThrows(IllegalStateException.class, () -> service.updateAvatar("u1", file));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(avatars).delete("new-avatar");
        verify(avatars, never()).delete("old-avatar");
    }

    @Test void failedUploadDoesNotModifyAvatar() {
        user.setAvatarPublicId("old-avatar");
        var file = new MockMultipartFile("file", new byte[]{1});
        when(avatars.upload("u1", file)).thenThrow(new AppException(ErrorCode.AVATAR_UPLOAD_FAILED));
        assertThrows(AppException.class, () -> service.updateAvatar("u1", file));
        assertEquals("old-avatar", user.getAvatarPublicId());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void unknownTransactionOutcomeDoesNotDeleteEitherAvatar() {
        TransactionSynchronizationManager.initSynchronization();
        user.setAvatarPublicId("old-avatar");
        var file = new MockMultipartFile("file", new byte[]{1});
        when(avatars.upload("u1", file)).thenReturn(new AvatarStorage.UploadedAvatar("https://new.example/a.png", "new-avatar"));
        service.updateAvatar("u1", file);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));
        verify(avatars, never()).delete(anyString());
    }

    @Test void createUserHashesPasswordAndAssignsRequestedRole() {
        when(roles.findByCode(RoleCode.TENANT)).thenReturn(Optional.of(Role.builder().code(RoleCode.TENANT).build()));
        when(links.save(any(UserRole.class))).thenAnswer(i -> i.getArgument(0));
        var response = service.create(new CreateUserRequest("New@Example.com", "Password@123", "New User", null, null, RoleCode.TENANT));
        assertEquals("new@example.com", response.email());
        assertEquals(List.of(RoleCode.TENANT), response.roles());
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        assertTrue(encoder.matches("Password@123", captor.getValue().getPassword()));
        assertEquals(UserStatus.ACTIVE, response.status());
    }
}
