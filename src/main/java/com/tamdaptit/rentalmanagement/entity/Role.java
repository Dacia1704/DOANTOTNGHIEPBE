package com.tamdaptit.rentalmanagement.entity;

import com.tamdaptit.rentalmanagement.entity.enums.RoleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Role {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    /** TENANT / LANDLORD / ADMIN */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    RoleCode code;

    @Column(nullable = false, length = 50)
    String name;

    @Column(length = 255)
    String description;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    /** N-N voi permissions qua bang role_permissions */
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    @Builder.Default
    Set<RolePermission> rolePermissions = new HashSet<>();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isDeleted == null) isDeleted = false;
    }
}