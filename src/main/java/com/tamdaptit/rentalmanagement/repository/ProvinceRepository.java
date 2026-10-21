package com.tamdaptit.rentalmanagement.repository;

import com.tamdaptit.rentalmanagement.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, String> {
    List<Province> findAllByOrderByCodeAsc();
}
