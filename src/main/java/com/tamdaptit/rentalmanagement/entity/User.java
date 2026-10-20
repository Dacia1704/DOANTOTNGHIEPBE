package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
import com.tamdaptit.rentalmanagement.entity.enums.UserStatus;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @Column(nullable = false, length = 100)
    String email;

    @Column(length = 20)
    String phone;

    /** BCrypt hash */
    @Column(nullable = false, length = 255)
    String password;

    @Column(name = "full_name", nullable = false, length = 100)
    String fullName;

    @Column(name = "avatar_url", length = 500)
    String avatarUrl;

    @Column(name = "avatar_public_id", length = 255)
    String avatarPublicId;

    @Column(name = "citizen_id", length = 20)
    String citizenId;

    @Column(name = "email_verified_at")
    LocalDateTime emailVerifiedAt;

    @Column(name = "phone_verified_at")
    LocalDateTime phoneVerifiedAt;

    /** ACTIVE / INACTIVE / BLOCKED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    UserStatus status = UserStatus.ACTIVE;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;

    /**
     * Quan he N:N toi Role qua bang trung gian user_roles.
     * Mot user co the vua la TENANT vua la LANDLORD dong thoi.
     */
    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    Set<UserRole> userRoles = new HashSet<>();

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    Set<RefreshToken> refreshTokens = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = UserStatus.ACTIVE;
        if (isDeleted == null) isDeleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}