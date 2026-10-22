package com.tamdaptit.rentalmanagement.auth.repository;

import com.tamdaptit.rentalmanagement.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    void deleteAllByUser_Id(String userId);

    Optional<RefreshToken> findByTokenAndRevokedFalse(String tokenHash);

    /**
     * Khoá dòng token khi xoay vòng.
     *
     * <p>Khong co khoa thi hai request dong thoi deu doc {@code revoked = false}
     * va ca hai deu cap cap token moi, khong chan duoc viec tai su dung token cu.
     *
     * @param tokenHash SHA-256 hex cua token, xem {@code HashUtils.sha256Hex}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.token = :tokenHash AND rt.revoked = false")
    Optional<RefreshToken> findByTokenForUpdate(@Param("tokenHash") String tokenHash);

    boolean existsByToken(String tokenHash);

    List<RefreshToken> findAllByUser_IdAndRevokedFalse(String userId);

    /**
     * Thu hoi tat ca token cua mot user (dang xuat o moi thiet bi).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true "
            + "WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllByUserId(@Param("userId") String userId);

    /**
     * Xoa cac token da het han.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
