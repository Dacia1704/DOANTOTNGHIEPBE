package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
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

/**
 * Thong ke theo ngay.
 * stat_date = ngay du lieu thuoc ve (KHAC created_at).
 * Unique (listing_id, stat_date) dam bao 1 listing co nhieu ban ghi theo ngay.
 */
@Entity
@Table(
    name = "listing_daily_stats",
    uniqueConstraints = @UniqueConstraint(
        name = "UQ_lds_listing_date",
        columnNames = {"listing_id", "stat_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ListingDailyStat {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    Listing listing;

    @Column(name = "stat_date", nullable = false)
    LocalDate statDate;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    Long viewCount = 0L;

    @Column(name = "contact_count", nullable = false)
    @Builder.Default
    Long contactCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (viewCount == null) viewCount = 0L;
        if (contactCount == null) contactCount = 0L;
    }
}