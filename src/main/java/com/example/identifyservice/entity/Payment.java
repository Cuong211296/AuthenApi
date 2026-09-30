package com.example.identifyservice.entity;

import com.example.identifyservice.enums.PaymentAttemptStatus;
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
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    Order order;

    @Column(nullable = false, length = 20)
    @Builder.Default
    String provider = "MOMO";

    @Column(nullable = false, length = 64)
    String requestId;

    /** The orderId sent to MoMo for this attempt (unique per attempt, so retries never collide). */
    @Column(nullable = false, unique = true, length = 80)
    String providerOrderId;

    Long transId;

    long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    PaymentAttemptStatus status;

    @Column(length = 4000)
    String rawResponse;

    @CreationTimestamp
    @Column(updatable = false)
    Instant createdAt;
}
