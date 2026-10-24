package com.tamdaptit.rentalmanagement.subscription.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServicePackage {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "max_listings")
    @Builder.Default
    private Integer maxListings = 0;

    @Column(name = "high_priority_slots", nullable = false)
    @Builder.Default
    private Integer highPrioritySlots = 0;

    @Column(name = "low_priority_slots", nullable = false)
    @Builder.Default
    private Integer lowPrioritySlots = 0;

    @Column(name = "default_priority_level", nullable = false)
    @Builder.Default
    private Integer defaultPriorityLevel = 0;

    @Column(name = "has_stats", nullable = false)
    @Builder.Default
    private Boolean hasStats = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
