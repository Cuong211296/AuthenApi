package com.example.identifyservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** The shop's own settings: a single row with the fixed id {@code "main"}. Every column is nullable (no settings yet). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "shop_settings")
public class ShopSettings {
    @Id
    @Column(length = 20)
    String id;

    @Column(length = 100)
    String shopName;

    @Column(length = 20)
    String phone;

    Integer provinceId;

    Integer districtId;

    @Column(length = 20)
    String wardCode;

    @Column(length = 100)
    String provinceName;

    @Column(length = 100)
    String districtName;

    @Column(length = 100)
    String wardName;

    @Column(length = 300)
    String address;

    Instant updatedAt;

    @Column(length = 100)
    String updatedBy;
}
