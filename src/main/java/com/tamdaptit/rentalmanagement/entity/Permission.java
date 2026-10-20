package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @Column(nullable = false, length = 50)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    @Column(length = 255)
    String description;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @jakarta.persistence.PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isDeleted == null) isDeleted = false;
        if (id == null) id = UUID.randomUUID().toString();
    }
}