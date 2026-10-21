package com.tamdaptit.RentalManagement.service;

import com.tamdaptit.rentalmanagement.common.*;
import com.tamdaptit.rentalmanagement.controller.UserController;
import com.tamdaptit.rentalmanagement.dto.user.*;
import com.tamdaptit.rentalmanagement.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.exception.GlobalExceptionHandler;
import com.tamdaptit.rentalmanagement.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(UserControllerSecurityTest.Config.class)
class UserControllerSecurityTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({UserController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401))).build();
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired UserService service;
    MockMvc mvc;
    UserResponse response = new UserResponse("user-id", "u@example.com", null, "Name", null, null,
            UserStatus.ACTIVE, null, null, null, null, List.of());
    org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        return authentication(new UsernamePasswordAuthenticationToken("user-id", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @BeforeEach void setup() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(service.get("user-id")).thenReturn(response);
    }

    @Test void anonymousCannotReadMe() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void tenantCanReadOwnProfileAndNoSecretsAreSerialized() throws Exception {
        mvc.perform(get("/api/users/get/me").with(as("TENANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("user-id"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.avatarPublicId").doesNotExist());
        verify(service).get("user-id");
    }

    @Test void tenantCannotReadOrModifyOtherUsers() throws Exception {
        mvc.perform(get("/api/users/other-id").with(as("TENANT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(as("TENANT"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/other-id").with(as("TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/other-id").with(as("TENANT"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void adminCanCreateAndReadUsers() throws Exception {
        var request = new CreateUserRequest("new@example.com", "Password@123", "Name", null, null,
                com.tamdaptit.rentalmanagement.entity.enums.RoleCode.TENANT);
        when(service.create(request)).thenReturn(response);
        mvc.perform(post("/api/users").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"password\":\"Password@123\",\"fullName\":\"Name\",\"roleCode\":\"TENANT\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201));
        mvc.perform(get("/api/users/user-id").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test void profileUpdateUsesPrincipalAndIgnoresPrivilegeFields() throws Exception {
        var request = new UpdateProfileRequest("New Name", null, null);
        when(service.updateProfile("user-id", request)).thenReturn(response);
        mvc.perform(put("/api/users/update/me").with(as("TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"New Name\",\"id\":\"other-id\",\"roleCode\":\"ADMIN\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        verify(service).updateProfile("user-id", request);
    }

    @Test void validatesPasswordRequest() throws Exception {
        mvc.perform(put("/api/users/me/password").with(as("TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void passwordChangeCallsOnlyCurrentUser() throws Exception {
        mvc.perform(put("/api/users/me/password").with(as("TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"OldPassword@123\",\"newPassword\":\"NewPassword@123\",\"confirmPassword\":\"NewPassword@123\"}"))
                .andExpect(status().isOk());
        verify(service).changePassword("user-id", new ChangePasswordRequest("OldPassword@123", "NewPassword@123", "NewPassword@123"));
    }

    @Test void multipartAvatarUsesAuthenticatedUserAndRequiresFile() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        when(service.updateAvatar("user-id", file)).thenReturn(response);
        mvc.perform(multipart("/api/users/me/avatar").file(file).with(as("TENANT")))
                .andExpect(status().isOk());
        verify(service).updateAvatar("user-id", file);
        mvc.perform(multipart("/api/users/me/avatar").with(as("TENANT"))).andExpect(status().isBadRequest());
    }
}
