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
}
