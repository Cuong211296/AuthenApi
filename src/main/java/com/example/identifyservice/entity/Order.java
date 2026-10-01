package com.example.identifyservice.entity;

import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.enums.ShippingSource;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(name = "ShopOrder")
@Table(name = "orders", indexes = @Index(name = "idx_order_status", columnList = "status"))
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, unique = true, length = 40)
    String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    PaymentStatus paymentStatus;

    long subtotal;
    long shippingFee;
    long total;

    @Column(nullable = false, length = 100)
    String receiverName;

    @Column(nullable = false, length = 20)
    String phone;

    @Column(nullable = false, length = 150)
    String email;

    @Column(nullable = false, length = 300)
    String address;

    @Column(nullable = false, length = 100)
    String province;

    /** Null on orders created before the ward field existed. */
    @Column(length = 100)
    String ward;

    /** Quận/Huyện; null on orders created before GHN address selection or in text mode without it. */
    @Column(length = 100)
    String district;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    ShippingSource shippingSource;

    /** Parcel weight used for the shipping quote; null on old orders. */
    @Column
    Integer weightGrams;

    @Column(length = 500)
    String note;

    @CreationTimestamp
    @Column(updatable = false)
    Instant createdAt;

    Instant paidAt;

    Instant expiresAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<OrderItem> items = new ArrayList<>();
}
