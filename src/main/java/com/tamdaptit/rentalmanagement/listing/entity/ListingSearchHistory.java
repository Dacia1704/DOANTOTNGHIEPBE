package com.tamdaptit.rentalmanagement.listing.entity;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lich su tim kiem cua tenant. Spring ghi moi lan tenant tim kiem.
 *
 * <p>AI service khong co quyen ghi vao bang nay: khi chatbot tim phong, no ghi
 * bo loc vao {@code ai_messages.filters_json} trong schema {@code rental_ai}.
 */
@Entity
@Table(name = "listing_search_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ListingSearchHistory {

    /** Nguon cua lan tim kiem. */
    public enum Source {
        SEARCH_PAGE,
        MAP,
        CHATBOT
    }

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private Source source = Source.SEARCH_PAGE;

    @Column(name = "keyword", length = 255)
    private String keyword;

    @Column(name = "province_id", length = 36)
    private String provinceId;

    @Column(name = "ward_id", length = 36)
    private String wardId;

    @Column(name = "price_min", precision = 12, scale = 2)
    private BigDecimal priceMin;

    @Column(name = "price_max", precision = 12, scale = 2)
    private BigDecimal priceMax;

    @Column(name = "area_min", precision = 8, scale = 2)
    private BigDecimal areaMin;

    @Column(name = "area_max", precision = 8, scale = 2)
    private BigDecimal areaMax;

    @Column(name = "room_type_id", length = 36)
    private String roomTypeId;

    @Column(name = "result_count")
    private Integer resultCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
