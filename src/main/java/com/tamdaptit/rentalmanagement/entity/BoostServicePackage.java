package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "boost_service_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BoostServicePackage {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @Column(nullable = false, length = 30, unique = true)
    String code;

    @Column(nullable = false, length = 100)
    String name;

    @Column(name = "priority_level", nullable = false)
    @Builder.Default
    Integer priorityLevel = 0;

    @Column(name = "duration_days", nullable = false)
    Integer durationDays;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal price;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (priorityLevel == null) priorityLevel = 0;
        if (isActive == null) isActive = true;
    }
}