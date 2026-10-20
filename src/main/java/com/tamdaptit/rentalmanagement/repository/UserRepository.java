package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    boolean existsByEmailAndIsDeletedFalse(String email);

    Optional<User> findByPhoneAndIsDeletedFalse(String phone);

    boolean existsByPhoneAndIsDeletedFalse(String phone);

    boolean existsByCitizenIdAndIsDeletedFalse(String citizenId);

    Optional<User> findByIdAndIsDeletedFalse(String id);

    List<User> findAllByIsDeletedFalse();

    /**
     * Load user kem Role va Permission qua user_roles -> role_permissions.
     * Dung trong login / refresh de tranh N+1 query.
     */
    @Query("""
            SELECT DISTINCT u
            FROM User u
            LEFT JOIN FETCH u.userRoles ur
            LEFT JOIN FETCH ur.role r
            LEFT JOIN FETCH r.rolePermissions rp
            LEFT JOIN FETCH rp.permission p
            WHERE u.id = :id AND u.isDeleted = false
            """)
    Optional<User> findByIdWithRolesAndPermissions(@Param("id") String id);

    @Query("""
            SELECT DISTINCT u
            FROM User u
            LEFT JOIN FETCH u.userRoles ur
            LEFT JOIN FETCH ur.role r
            LEFT JOIN FETCH r.rolePermissions rp
            LEFT JOIN FETCH rp.permission p
            WHERE u.email = :email AND u.isDeleted = false
            """)
    Optional<User> findByEmailWithRolesAndPermissions(@Param("email") String email);
}