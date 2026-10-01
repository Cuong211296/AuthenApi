package com.example.identifyservice.repository;

import com.example.identifyservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    /** Stats: creation instants of non-admin users created in [from, to). */
    @org.springframework.data.jpa.repository.Query("""
            select u.createdAt from User u
            where u.createdAt >= :from and u.createdAt < :to
              and not exists (select r.id from u.roles r where r.name = 'ADMIN')
            """)
    java.util.List<java.time.Instant> findCustomerCreatedAtBetween(
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to);
}
