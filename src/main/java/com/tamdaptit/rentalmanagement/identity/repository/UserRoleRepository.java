package com.tamdaptit.rentalmanagement.identity.repository;

import com.tamdaptit.rentalmanagement.identity.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, String> {
    void deleteAllByUser_Id(String userId);

    boolean existsByUser_IdAndRole_Id(String userId, String roleId);

    List<UserRole> findAllByUser_Id(String userId);

    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId")
    List<UserRole> findAllByUserIdWithQuery(@Param("userId") String userId);
}
