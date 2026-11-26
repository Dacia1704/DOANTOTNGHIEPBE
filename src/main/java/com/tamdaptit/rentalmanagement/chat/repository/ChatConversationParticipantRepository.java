package com.tamdaptit.rentalmanagement.chat.repository;

import java.util.List;
import java.util.Optional;

import com.tamdaptit.rentalmanagement.chat.entity.ChatConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatConversationParticipantRepository extends JpaRepository<ChatConversationParticipant, String> {
    List<ChatConversationParticipant> findByConversationId(String conversationId);

    Optional<ChatConversationParticipant> findByConversationIdAndUserId(String conversationId, String userId);

    boolean existsByConversationIdAndUserId(String conversationId, String userId);

    /**
     * Lấy participant "còn lại" trong conversation (không phải current user).
     */
    @Query("""
            SELECT p
            FROM ChatConversationParticipant p
            WHERE p.conversation.id = :conversationId
              AND p.user.id <> :userId
            """)
    Optional<ChatConversationParticipant> findOtherParticipant(
        @Param("conversationId") String conversationId,
        @Param("userId") String userId
    );
}
