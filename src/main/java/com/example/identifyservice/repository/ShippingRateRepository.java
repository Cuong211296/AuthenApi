package com.example.identifyservice.repository;

import com.example.identifyservice.entity.ShippingRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShippingRateRepository extends JpaRepository<ShippingRate, String> {
    Optional<ShippingRate> findByProvinceIgnoreCase(String province);
    boolean existsByProvinceIgnoreCase(String province);
    List<ShippingRate> findAllByOrderByProvinceAsc();
}
