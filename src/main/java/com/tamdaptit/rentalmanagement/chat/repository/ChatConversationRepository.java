package com.tamdaptit.rentalmanagement.chat.repository;

import com.tamdaptit.rentalmanagement.chat.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, String> {

    /**
     * Lấy danh sách conversations của một user, sort theo lastMessageAt DESC (nulls last).
     */
    @Query("""
            SELECT DISTINCT c
            FROM ChatConversation c
            JOIN c.participants p
            WHERE p.user.id = :userId
            ORDER BY CASE WHEN c.lastMessageAt IS NULL THEN c.createdAt ELSE c.lastMessageAt END DESC
            """)
    List<ChatConversation> findConversationsByUserId(@Param("userId") String userId);

    /**
     * Tìm Listing conversation đã tồn tại giữa 2 user và 1 listing.
     * Đảm bảo cả 2 user đều là participant trong conversation đó.
     */
    @Query("""
            SELECT c
            FROM ChatConversation c
            WHERE c.listing.id = :listingId
              AND (SELECT COUNT(p) FROM ChatConversationParticipant p WHERE p.conversation = c AND p.user.id IN (:userIdA, :userIdB)) = 2
              AND (SELECT COUNT(p) FROM ChatConversationParticipant p WHERE p.conversation = c) = 2
            """)
    Optional<ChatConversation> findListingConversation(
            @Param("listingId") String listingId,
            @Param("userIdA") String userIdA,
            @Param("userIdB") String userIdB
    );

    /**
     * Tìm Direct conversation (listing=null, rentalRequest=null) giữa 2 user.
     * Thứ tự A+B hoặc B+A đều nhận kết quả giống nhau.
     */
    @Query("""
            SELECT c
            FROM ChatConversation c
            WHERE c.listing IS NULL
              AND c.rentalRequest IS NULL
              AND (SELECT COUNT(p) FROM ChatConversationParticipant p WHERE p.conversation = c AND p.user.id IN (:userIdA, :userIdB)) = 2
              AND (SELECT COUNT(p) FROM ChatConversationParticipant p WHERE p.conversation = c) = 2
            """)
    Optional<ChatConversation> findDirectConversation(
            @Param("userIdA") String userIdA,
            @Param("userIdB") String userIdB
    );
}
