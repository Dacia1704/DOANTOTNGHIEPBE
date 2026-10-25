package com.tamdaptit.rentalmanagement.chat.entity;

import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.rental.entity.RentalRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatConversation {

    @Id
    @UuidGenerator
    @Column(length = 36)
    String id;

    /**
     * Có giá trị khi conversation được tạo từ một bài đăng.
     * Direct chat (ví dụ Tenant -> Admin) thì null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id")
    Listing listing;

    /**
     * Có giá trị nếu conversation liên quan tới một rental request.
     * Chat thông thường thì null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_request_id")
    RentalRequest rentalRequest;

    @Column(name = "last_message_at")
    LocalDateTime lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;

    @OneToMany(mappedBy = "conversation")
    @Builder.Default
    List<ChatConversationParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "conversation")
    @Builder.Default
    List<ChatMessage> messages = new ArrayList<>();
}