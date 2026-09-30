package com.example.identifyservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "invalidated_token", indexes = {
    @Index(name = "idx_token_expiry", columnList = "expiryTime"),
    @Index(name = "idx_token_user", columnList = "userId")
})
public class InvalidatedToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, unique = true, length = 64)
    String jti; // JWT ID

    @Column(nullable = false)
    Instant expiryTime;

    @Column(length = 100)
    String userId; // Optional: track which user invalidated the token

    @Column(length = 100)
    String reason; // LOGOUT, REFRESH, PASSWORD_CHANGE, etc.

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    Instant createdAt;
}
