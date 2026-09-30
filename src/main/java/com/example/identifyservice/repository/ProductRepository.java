package com.example.identifyservice.repository;

import com.example.identifyservice.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"variants", "category"})
    Optional<Product> findBySlugAndActiveTrue(String slug);

    @EntityGraph(attributePaths = {"variants", "category"})
    Optional<Product> findWithDetailsById(String id);

    @Query("""
            select p from Product p left join p.category c
            where p.active = true
              and (:categorySlug = '' or c.slug = :categorySlug)
              and (:q = '' or lower(p.name) like lower(concat('%', :q, '%')))
            """)
    Page<Product> search(@Param("categorySlug") String categorySlug, @Param("q") String q, Pageable pageable);
}
