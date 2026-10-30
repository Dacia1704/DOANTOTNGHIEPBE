package com.tamdaptit.rentalmanagement.chat.repository;

import com.tamdaptit.rentalmanagement.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    /**
     * Lấy messages của conversation theo trang, sort mới nhất trước.
     */
    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    /**
     * Lấy tin nhắn cuối cùng của conversation.
     */
    @Query("""
            SELECT m
            FROM ChatMessage m
            WHERE m.conversation.id = :conversationId
            ORDER BY m.createdAt DESC
            LIMIT 1
            """)
    Optional<ChatMessage> findLatestMessage(@Param("conversationId") String conversationId);

    /**
     * Đếm số tin nhắn chưa đọc của participant trong conversation.
     * Unread = message từ người khác, sau lastReadAt (hoặc joinedAt nếu chưa đọc lần nào).
     */
    @Query("""
            SELECT COUNT(m)
            FROM ChatMessage m
            WHERE m.conversation.id = :conversationId
              AND m.sender.id <> :userId
              AND m.createdAt > :cutoffTime
            """)
    long countUnread(
            @Param("conversationId") String conversationId,
            @Param("userId") String userId,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );
}
