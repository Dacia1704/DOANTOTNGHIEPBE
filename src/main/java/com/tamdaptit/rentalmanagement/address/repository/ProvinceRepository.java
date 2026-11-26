package com.tamdaptit.rentalmanagement.address.repository;

import java.util.List;

import com.tamdaptit.rentalmanagement.address.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<Province, String> {
    List<Province> findAllByOrderByCodeAsc();
}
