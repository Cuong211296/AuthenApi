package com.example.identifyservice.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {
    @Test
    void effectiveWeightFallsBackToDefault() {
        Product p = Product.builder().name("x").slug("x").build();
        assertThat(Product.DEFAULT_WEIGHT_GRAMS).isEqualTo(300);
        assertThat(p.effectiveWeight()).isEqualTo(300);
        p.setWeightGrams(850);
        assertThat(p.effectiveWeight()).isEqualTo(850);
    }
}
