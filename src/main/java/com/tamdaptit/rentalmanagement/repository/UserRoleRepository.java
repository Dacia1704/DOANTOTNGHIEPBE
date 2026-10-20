package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.UserRole;
import com.tamdaptit.rentalmanagement.entity.UserRoleId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    boolean existsByUserIdAndRoleId(String userId, String roleId);

    List<UserRole> findAllByIdUserId(String userId);

    Optional<UserRole> findByIdUserIdAndIdRoleId(String userId, String roleId);

    @Query("SELECT ur FROM UserRole ur WHERE ur.id.userId = :userId")
    List<UserRole> findAllByUserIdWithQuery(@Param("userId") String userId);
}