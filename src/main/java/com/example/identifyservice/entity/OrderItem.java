package com.example.identifyservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "order_item")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    Order order;

    /** Snapshot of the purchased variant (not a foreign key, so catalog edits never break history). */
    @Column(nullable = false, length = 36)
    String variantId;

    @Column(nullable = false, length = 200)
    String productName;

    @Column(name = "size_value", nullable = false, length = 20)
    String size;

    @Column(name = "color_value", nullable = false, length = 50)
    String color;

    long unitPrice;

    int quantity;

    /** Snapshot of the product cost price at checkout; null when unknown. Never exposed to customers. */
    Long unitCost;
}
