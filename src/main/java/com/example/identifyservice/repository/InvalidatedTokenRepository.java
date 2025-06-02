package com.example.identifyservice.repository;

import com.example.identifyservice.entity.InvalidatedToken;
import com.example.identifyservice.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {

}
