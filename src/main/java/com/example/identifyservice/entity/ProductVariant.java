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
@Table(name = "product_variant", uniqueConstraints =
        @UniqueConstraint(name = "uk_variant_combo", columnNames = {"product_id", "size_value", "color_value"}))
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    Product product;

    @Column(name = "size_value", nullable = false, length = 20)
    String size;

    @Column(name = "color_value", nullable = false, length = 50)
    String color;

    @Column(nullable = false, unique = true, length = 64)
    String sku;

    @Column(nullable = false)
    int stock;

    /** Optional override of the product's base price. */
    Long price;

    @Column(nullable = false)
    @Builder.Default
    boolean active = true;

    public long effectivePrice() {
        return price != null ? price : product.getBasePrice();
    }
}
