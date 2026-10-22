package com.tamdaptit.rentalmanagement.listing.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Thong ke theo ngay.
 * stat_date = ngay du lieu thuoc ve (KHAC created_at).
 * Unique (listing_id, stat_date) dam bao 1 listing co nhieu ban ghi theo ngay.
 */
@Entity
@Table(name = "listing_daily_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ListingDailyStat {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "contact_count")
    private Integer contactCount = 0;
}
