package com.tamdaptit.rentalmanagement.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tamdaptit.rentalmanagement.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.entity.enums.UserStatus;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterResponse {

    String id;

    String email;

    String phone;

    String fullName;

    UserStatus status;

    /** Vai tro duoc gan khi dang ky (TENANT hoac LANDLORD) */
    RoleCode roleCode;

    /** Danh sach authorities: ROLE_TENANT + cac permission tuong ung */
    List<String> authorities;

    String accessToken;

    String refreshToken;
}