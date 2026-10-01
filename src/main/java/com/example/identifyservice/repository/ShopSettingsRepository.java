package com.example.identifyservice.repository;

import com.example.identifyservice.entity.ShopSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopSettingsRepository extends JpaRepository<ShopSettings, String> {
}
