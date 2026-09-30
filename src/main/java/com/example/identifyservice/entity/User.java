package com.example.identifyservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "user", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true),
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_status", columnList = "status")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, unique = true, length = 100)
    String username;

    @Column(nullable = false)
    String password;

    @Column(length = 100)
    String firstname;

    @Column(length = 100)
    String lastname;

    @Column(nullable = false)
    LocalDate dob;

    @Column(unique = true, length = 150)
    String email;

    @Column(length = 20)
    String phone;

    @Column(nullable = false, length = 50)
    @Builder.Default
    String status = "ACTIVE"; // ACTIVE, INACTIVE, SUSPENDED, DELETED

    @Column(length = 50)
    String loginMethod; // LOCAL, GOOGLE, GITHUB, FACEBOOK, etc.

    @CreationTimestamp
    @Column(updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    Instant updatedAt;

    Instant deletedAt; // For soft delete

    Instant lastLoginAt;

    @Column(columnDefinition = "INT DEFAULT 0")
    Integer loginAttempts;

    Instant lockedUntil; // temporary lock after repeated failed logins

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_role",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    Set<Role> roles;
}
