package com.example.identifyservice.repository;

import com.example.identifyservice.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {
    boolean existsBySku(String sku);

    boolean existsByProductIdAndSizeAndColor(String productId, String size, String color);

    /** Atomic: returns 0 when there is not enough stock. Does not clear the persistence context. */
    @Modifying
    @Query("update ProductVariant v set v.stock = v.stock - :qty where v.id = :id and v.stock >= :qty")
    int decrementStock(@Param("id") String id, @Param("qty") int qty);

    @Modifying
    @Query("update ProductVariant v set v.stock = v.stock + :qty where v.id = :id")
    int incrementStock(@Param("id") String id, @Param("qty") int qty);

    /** Stats: active variants of active products with stock at or below the threshold, lowest first. */
    @Query("""
            select v from ProductVariant v join fetch v.product p
            where v.active = true and p.active = true and v.stock <= :threshold
            order by v.stock asc, p.name asc, v.sku asc
            """)
    java.util.List<com.example.identifyservice.entity.ProductVariant> findLowStock(
            @Param("threshold") int threshold, org.springframework.data.domain.Pageable pageable);
}
