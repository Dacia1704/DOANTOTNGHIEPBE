package com.tamdaptit.rentalmanagement.address.repository;

import com.tamdaptit.rentalmanagement.address.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, String> {
    List<Province> findAllByOrderByCodeAsc();
}
