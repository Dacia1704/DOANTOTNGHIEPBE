package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserRoleId implements Serializable {

    @Column(name = "user_id", length = 36)
    String userId;

    @Column(name = "role_id", length = 36)
    String roleId;
}