package com.tamdaptit.rentalmanagement.auth.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "IX_refresh_tokens_token", columnList = "token"),
        @Index(name = "IX_refresh_tokens_user_id", columnList = "user_id"),
        @Index(name = "IX_refresh_tokens_expires_at", columnList = "expires_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshToken {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    /**
     * SHA-256 hex cua refresh token (64 ky tu), khong phai token goc.
     * Xem {@code HashUtils.sha256Hex}.
     */
    @Column(name = "token", nullable = false, length = 64)
    String token;

    @Column(name = "expires_at", nullable = false)
    LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    Boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (revoked == null) {
            revoked = false;
        }
    }
}
