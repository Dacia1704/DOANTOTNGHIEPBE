package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    void deleteAllByUser_Id(String userId);

    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    boolean existsByToken(String token);

    List<RefreshToken> findAllByUser_IdAndRevokedFalse(String userId);

    /**
     * Revoke tat ca token cua mot user (dang xuat o moi thiet bi).
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllByUserId(@Param("userId") String userId);

    /**
     * Xoa cac token da het han (chay dinh ky qua job).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
