package com.tamdaptit.rentalmanagement.identity.repository;

import com.tamdaptit.rentalmanagement.identity.entity.TenantProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantProfileRepository extends JpaRepository<TenantProfile, String> {
    Optional<TenantProfile> findByUserId(String userId);
}
