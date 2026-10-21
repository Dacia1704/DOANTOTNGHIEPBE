package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.UserRole;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, String> {

    boolean existsByUser_IdAndRole_Id(String userId, String roleId);

    List<UserRole> findAllByUser_Id(String userId);

    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId")
    List<UserRole> findAllByUserIdWithQuery(@Param("userId") String userId);
}
