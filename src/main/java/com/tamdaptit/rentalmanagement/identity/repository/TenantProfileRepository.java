package com.tamdaptit.rentalmanagement.identity.repository;

import java.util.Optional;

import com.tamdaptit.rentalmanagement.identity.entity.TenantProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantProfileRepository extends JpaRepository<TenantProfile, String> {
    Optional<TenantProfile> findByUserId(String userId);
}
