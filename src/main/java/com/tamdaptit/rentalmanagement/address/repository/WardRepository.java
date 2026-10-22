package com.tamdaptit.rentalmanagement.address.repository;

import com.tamdaptit.rentalmanagement.address.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface WardRepository extends JpaRepository<Ward, String>, JpaSpecificationExecutor<Ward> {
    boolean existsByCodeAndIdNot(String code, String id);

    @Query(value = "SELECT (EXISTS(SELECT 1 FROM rooms WHERE ward_id = :id) "
            + "OR EXISTS(SELECT 1 FROM tenant_profiles WHERE desired_ward_id = :id))", nativeQuery = true)
    int countReferences(String id);
}
