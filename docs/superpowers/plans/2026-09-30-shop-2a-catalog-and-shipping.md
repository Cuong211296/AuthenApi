# Clothing Shop - Plan 2a: Catalog and Shipping

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Products with size/color variants and stock, categories, public browse/search API, admin CRUD, and shipping fee by province.

**Architecture:** New JPA entities and services in the existing layered packages. Public reads are `GET` endpoints open in `SecurityConfig`; everything under `/admin/**` requires role ADMIN (URL rule plus `@PreAuthorize`). Responses are hand-mapped records.

**Tech Stack:** Spring Boot 3.2.3, Spring Data JPA, JUnit 5 + H2 (profile `test`), Spring Security Test.

**Spec:** `docs/superpowers/specs/2026-09-30-clothing-shop-design.md` (sections 3, 4)

**Prerequisite:** Plan 1 complete (branch `feature/clothing-shop`, all tests green).

## Global Constraints

- Context path `/identity`; responses wrapped in `ApiResponse`; errors via `AppException(ErrorCode.X)`.
- Money is `long` VND. Entity IDs are UUID strings. New DTOs are Java records; validation messages are the literal `"INVALID_INPUT"` (the `GlobalExceptionHandler` maps a message equal to an `ErrorCode` name).
- Slugs match `^[a-z0-9]+(-[a-z0-9]+)*$`.
- Product/variant "delete" is a soft delete (`active=false`), never a hard delete.
- Spring tests: `@SpringBootTest @ActiveProfiles("test")`, class names end in `Test`. Assertions on shared data use `contains`/`doesNotContain`, never exact counts (the H2 database is shared by non-transactional tests).
- Commits end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- Inactive product or inactive variant must never appear in public lists/detail (Task 5 test).
- A variant with its own price overrides `basePrice`; a variant without one falls back (Task 5 test).
- Search text containing `%`, quotes, or only spaces must not error or match everything unexpectedly (Task 5 test).
- Duplicate slug/SKU on create and on update-to-existing are rejected (Task 6 test).
- Non-admins and anonymous callers cannot reach `/admin/**` (Task 6 test).
- Province lookup tolerates case and surrounding spaces but rejects unknown provinces (Task 7 test).

(Paths below are under `src/main/java/com/example/identifyservice/` for main and `src/test/java/com/example/identifyservice/` for tests.)

---

### Task 5: Catalog domain and public read API

**Files:**
- Create: `entity/Category.java`, `entity/Product.java`, `entity/ProductVariant.java`, `repository/CategoryRepository.java`, `repository/ProductRepository.java`, `repository/ProductVariantRepository.java`, `dto/response/PageResponse.java`, `dto/response/CategoryResponse.java`, `dto/response/VariantResponse.java`, `dto/response/ProductSummaryResponse.java`, `dto/response/ProductDetailResponse.java`, `service/ProductService.java`, `controller/ProductController.java`
- Modify: `dto/request/ApiResponse.java`, `configuration/SecurityConfig.java`
- Test: `testsupport/TestDataFactory.java`, `service/ProductServiceTest.java`, `controller/PublicCatalogSecurityTest.java`

**Interfaces:**
- Produces:
  - `ApiResponse.ok(T)`; `PageResponse<T>(List<T> items, int page, int size, int totalPages, long totalElements)` with `PageResponse.of(Page<T>)`
  - `ProductService.search(String categorySlug, String q, int page, int size) : PageResponse<ProductSummaryResponse>`; `ProductService.getBySlug(String) : ProductDetailResponse`; `ProductService.categories() : List<CategoryResponse>`
  - `ProductVariant.effectivePrice() : long`
  - `TestDataFactory.category(String slug)`, `.product(String slug, long price, boolean active)`, `.product(slug, price, active, categorySlug)`, `.variant(Product, size, color, stock, Long price)`, `.user(String username)`

- [ ] **Step 1: Create the entities**

`entity/Category.java`:
```java
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
@Table(name = "category")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, length = 100)
    String name;

    @Column(nullable = false, unique = true, length = 120)
    String slug;
}
```

`entity/Product.java`:
```java
package com.example.identifyservice.entity;

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
@Entity
@Table(name = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, length = 200)
    String name;

    @Column(nullable = false, unique = true, length = 220)
    String slug;

    @Column(length = 4000)
    String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    Category category;

    @Column(nullable = false)
    long basePrice;

    @Column(length = 500)
    String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    boolean active = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<ProductVariant> variants = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    Instant createdAt;
}
```

`entity/ProductVariant.java`:
```java
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
```

- [ ] **Step 2: Create the repositories**

`repository/CategoryRepository.java`:
```java
package com.example.identifyservice.repository;

import com.example.identifyservice.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
```

`repository/ProductRepository.java`:
```java
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
```

`repository/ProductVariantRepository.java`:
```java
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

    /** Atomic: returns 0 when there is not enough stock. Does not clear the persistence context. */
    @Modifying
    @Query("update ProductVariant v set v.stock = v.stock - :qty where v.id = :id and v.stock >= :qty")
    int decrementStock(@Param("id") String id, @Param("qty") int qty);

    @Modifying
    @Query("update ProductVariant v set v.stock = v.stock + :qty where v.id = :id")
    int incrementStock(@Param("id") String id, @Param("qty") int qty);
}
```

- [ ] **Step 3: Create the response records**

`dto/response/PageResponse.java`:
```java
package com.example.identifyservice.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, int totalPages, long totalElements) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalPages(), p.getTotalElements());
    }
}
```

`dto/response/CategoryResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Category;

public record CategoryResponse(String id, String name, String slug) {
    public static CategoryResponse from(Category c) {
        return c == null ? null : new CategoryResponse(c.getId(), c.getName(), c.getSlug());
    }
}
```

`dto/response/VariantResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.ProductVariant;

public record VariantResponse(String id, String size, String color, String sku, int stock, long price, boolean active) {
    public static VariantResponse from(ProductVariant v) {
        return new VariantResponse(v.getId(), v.getSize(), v.getColor(), v.getSku(), v.getStock(),
                v.effectivePrice(), v.isActive());
    }
}
```

`dto/response/ProductSummaryResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Product;

public record ProductSummaryResponse(String id, String name, String slug, String imageUrl, long basePrice,
                                     String categoryName, boolean active) {
    public static ProductSummaryResponse from(Product p) {
        return new ProductSummaryResponse(p.getId(), p.getName(), p.getSlug(), p.getImageUrl(), p.getBasePrice(),
                p.getCategory() == null ? null : p.getCategory().getName(), p.isActive());
    }
}
```

`dto/response/ProductDetailResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Product;

import java.util.List;

public record ProductDetailResponse(String id, String name, String slug, String description, String imageUrl,
                                    long basePrice, boolean active, CategoryResponse category,
                                    List<VariantResponse> variants) {
    /** publicView hides inactive variants. */
    public static ProductDetailResponse from(Product p, boolean publicView) {
        return new ProductDetailResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getImageUrl(),
                p.getBasePrice(), p.isActive(), CategoryResponse.from(p.getCategory()),
                p.getVariants().stream()
                        .filter(v -> !publicView || v.isActive())
                        .map(VariantResponse::from)
                        .toList());
    }
}
```

- [ ] **Step 4: Add `ApiResponse.ok`.** Inside `dto/request/ApiResponse.java`, before the closing brace:

```java
     public static <T> ApiResponse<T> ok(T result) {
          return ApiResponse.<T>builder().result(result).build();
     }
```

- [ ] **Step 5: Create the test data factory** `testsupport/TestDataFactory.java`

```java
package com.example.identifyservice.testsupport;

import com.example.identifyservice.entity.Category;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.repository.CategoryRepository;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TestDataFactory {
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired UserRepository users;

    public Category category(String slug) {
        return categories.findBySlug(slug).orElseGet(() ->
                categories.save(Category.builder().name(slug).slug(slug).build()));
    }

    public Product product(String slug, long price, boolean active) {
        return product(slug, price, active, "tops");
    }

    public Product product(String slug, long price, boolean active, String categorySlug) {
        return products.save(Product.builder().name("Product " + slug).slug(slug).basePrice(price)
                .category(category(categorySlug)).active(active).build());
    }

    public ProductVariant variant(Product p, String size, String color, int stock, Long price) {
        ProductVariant v = variants.save(ProductVariant.builder().product(p).size(size).color(color)
                .sku(p.getSlug() + "-" + size + "-" + color).stock(stock).price(price).build());
        p.getVariants().add(v);
        return v;
    }

    public User user(String username) {
        return users.findByUsername(username).orElseGet(() ->
                users.save(User.builder().username(username).password("x").dob(LocalDate.of(2000, 1, 1)).build()));
    }
}
```

- [ ] **Step 6: Write the failing service test** `service/ProductServiceTest.java`

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.dto.response.VariantResponse;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductServiceTest {
    @Autowired ProductService productService;
    @Autowired TestDataFactory data;
    @Autowired ProductVariantRepository variantRepository;

    @Test
    void listsOnlyActiveProducts() {
        data.product("tee-active", 199_000, true);
        data.product("tee-hidden", 199_000, false);
        var page = productService.search("", "", 0, 50);
        assertThat(page.items()).extracting(ProductSummaryResponse::slug)
                .contains("tee-active").doesNotContain("tee-hidden");
    }

    @Test
    void filtersByCategoryAndSearchText() {
        data.product("linen-shirt", 350_000, true, "tops");
        data.product("slim-jeans", 450_000, true, "bottoms");

        assertThat(productService.search("bottoms", "", 0, 50).items())
                .extracting(ProductSummaryResponse::slug).contains("slim-jeans").doesNotContain("linen-shirt");
        assertThat(productService.search("", "LINEN", 0, 50).items())
                .extracting(ProductSummaryResponse::slug).contains("linen-shirt").doesNotContain("slim-jeans");
    }

    @Test
    void oddSearchTextDoesNotFailOrMatchEverything() {
        data.product("plain-tee", 100_000, true);
        assertThat(productService.search("", "   ", 0, 50).items()).isNotNull();
        assertThat(productService.search("", "%", 0, 50).items())
                .extracting(ProductSummaryResponse::slug).doesNotContain("plain-tee");
        assertThat(productService.search("", "' or 1=1 --", 0, 50).items()).isEmpty();
    }

    @Test
    void detailUsesEffectivePriceAndHidesInactiveVariants() {
        Product p = data.product("hoodie", 300_000, true);
        data.variant(p, "M", "black", 5, null);
        data.variant(p, "L", "black", 3, 320_000L);
        ProductVariant hidden = data.variant(p, "XL", "black", 9, null);
        hidden.setActive(false);
        variantRepository.save(hidden);

        ProductDetailResponse detail = productService.getBySlug("hoodie");
        assertThat(detail.variants()).extracting(VariantResponse::size).containsExactlyInAnyOrder("M", "L");
        assertThat(detail.variants()).filteredOn(v -> v.size().equals("M")).extracting(VariantResponse::price)
                .containsExactly(300_000L);
        assertThat(detail.variants()).filteredOn(v -> v.size().equals("L")).extracting(VariantResponse::price)
                .containsExactly(320_000L);
    }

    @Test
    void unknownOrInactiveSlugIsNotFound() {
        data.product("retired", 100_000, false);
        for (String slug : new String[]{"retired", "does-not-exist"}) {
            assertThatThrownBy(() -> productService.getBySlug(slug))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }
}
```

Also create `controller/PublicCatalogSecurityTest.java`:

```java
package com.example.identifyservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicCatalogSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void productsAndCategoriesArePublic() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(get("/categories")).andExpect(status().isOk());
    }

    @Test
    void missingProductReturns404NotAnAuthError() throws Exception {
        mvc.perform(get("/products/nope")).andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 7: Run to verify failure**

Run: `mvn -q test -Dtest=ProductServiceTest,PublicCatalogSecurityTest`
Expected: compilation error, `ProductService` missing.

- [ ] **Step 8: Implement `ProductService`**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.CategoryResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.CategoryRepository;
import com.example.identifyservice.repository.ProductRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductService {
    ProductRepository productRepository;
    CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(String categorySlug, String q, int page, int size) {
        String cat = categorySlug == null ? "" : categorySlug.trim();
        String text = q == null ? "" : q.trim();
        if (text.length() > 100) text = text.substring(0, 100);
        // % and _ are LIKE wildcards; treat them literally by not matching them at all.
        if (text.contains("%") || text.contains("_")) text = "\u0000no-match\u0000";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(productRepository.search(cat, text, pageable).map(ProductSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getBySlug(String slug) {
        var product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductDetailResponse.from(product, true);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }
}
```

- [ ] **Step 9: Implement `ProductController`**

```java
package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.response.CategoryResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductController {
    ProductService productService;

    @GetMapping("/products")
    ApiResponse<PageResponse<ProductSummaryResponse>> list(
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(productService.search(category, q, page, size));
    }

    @GetMapping("/products/{slug}")
    ApiResponse<ProductDetailResponse> detail(@PathVariable String slug) {
        return ApiResponse.ok(productService.getBySlug(slug));
    }

    @GetMapping("/categories")
    ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.ok(productService.categories());
    }
}
```

- [ ] **Step 10: Open the public GET routes.** In `SecurityConfig` replace the field `PUBLIC_ENDPOINTS` usage. Rename the existing array to `PUBLIC_POST_ENDPOINTS` (same contents) and add:

```java
    private final String[] PUBLIC_GET_ENDPOINTS = {"/products/**", "/categories/**", "/shipping/fee", "/shipping/provinces"};
```
and change the authorize block to:

```java
            httpSecurity.authorizeHttpRequests(request ->
                    request.requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                            .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                            .requestMatchers("/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated());
```

- [ ] **Step 11: Run to verify it passes**

Run: `mvn -q test -Dtest=ProductServiceTest,PublicCatalogSecurityTest`
Expected: 7 tests PASS. If the `%` search test fails on H2, the sentinel string branch in `search` is the cause: confirm it is reached.

- [ ] **Step 12: Commit**

```bash
git add src
git commit -m "feat: catalog entities and public product/category API"
```

---

### Task 6: Admin catalog CRUD

**Files:**
- Create: `dto/request/CategoryRequest.java`, `dto/request/ProductRequest.java`, `dto/request/VariantRequest.java`, `service/AdminCatalogService.java`, `controller/AdminCatalogController.java`
- Test: `service/AdminCatalogServiceTest.java`, `controller/AdminSecurityTest.java`

**Interfaces:**
- Consumes: entities/repositories from Task 5.
- Produces (all `@PreAuthorize("hasRole('ADMIN')")`):
  - `createCategory(CategoryRequest) : CategoryResponse`
  - `listProducts(int page, int size) : PageResponse<ProductSummaryResponse>` (includes inactive)
  - `getProduct(String id) : ProductDetailResponse` (all variants)
  - `createProduct(ProductRequest) : ProductDetailResponse`, `updateProduct(String id, ProductRequest)`, `deactivateProduct(String id)`
  - `createVariant(String productId, VariantRequest) : VariantResponse`, `updateVariant(String variantId, VariantRequest)`, `deactivateVariant(String variantId)`

- [ ] **Step 1: Create the request records**

`dto/request/CategoryRequest.java`:
```java
package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 100, message = "INVALID_INPUT") String name,
        @NotBlank(message = "INVALID_INPUT") @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "INVALID_INPUT") String slug) {
}
```

`dto/request/ProductRequest.java`:
```java
package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 200, message = "INVALID_INPUT") String name,
        @NotBlank(message = "INVALID_INPUT") @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "INVALID_INPUT") String slug,
        @Size(max = 4000, message = "INVALID_INPUT") String description,
        String categoryId,
        @Min(value = 0, message = "INVALID_INPUT") long basePrice,
        @Size(max = 500, message = "INVALID_INPUT") String imageUrl,
        Boolean active) {
}
```

`dto/request/VariantRequest.java`:
```java
package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VariantRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 20, message = "INVALID_INPUT") String size,
        @NotBlank(message = "INVALID_INPUT") @Size(max = 50, message = "INVALID_INPUT") String color,
        @NotBlank(message = "INVALID_INPUT") @Size(max = 64, message = "INVALID_INPUT") String sku,
        @Min(value = 0, message = "INVALID_INPUT") int stock,
        @Min(value = 0, message = "INVALID_INPUT") Long price,
        Boolean active) {
}
```

- [ ] **Step 2: Write the failing tests**

`service/AdminCatalogServiceTest.java`:
```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CategoryRequest;
import com.example.identifyservice.dto.request.ProductRequest;
import com.example.identifyservice.dto.request.VariantRequest;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminCatalogServiceTest {
    @Autowired AdminCatalogService admin;
    @Autowired ProductService publicService;

    private ProductRequest product(String slug, String categoryId) {
        return new ProductRequest("Name " + slug, slug, "desc", categoryId, 250_000, "https://img/x.jpg", true);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((AppException) t).getErrorCode();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanBuildCatalogAndItAppearsPublicly() {
        var cat = admin.createCategory(new CategoryRequest("Shirts", "shirts"));
        var created = admin.createProduct(product("oxford-shirt", cat.id()));
        var variant = admin.createVariant(created.id(), new VariantRequest("M", "blue", "OXF-M-BLUE", 10, null, null));

        assertThat(variant.price()).isEqualTo(250_000);
        assertThat(publicService.getBySlug("oxford-shirt").variants()).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateSlugAndSkuAreRejected() {
        var created = admin.createProduct(product("dup-tee", null));
        admin.createVariant(created.id(), new VariantRequest("M", "red", "DUP-1", 1, null, null));

        assertThatThrownBy(() -> admin.createProduct(product("dup-tee", null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SLUG_EXISTED);
        assertThatThrownBy(() -> admin.createVariant(created.id(), new VariantRequest("L", "red", "DUP-1", 1, null, null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SKU_EXISTED);

        var other = admin.createProduct(product("other-tee", null));
        assertThatThrownBy(() -> admin.updateProduct(other.id(), product("dup-tee", null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SLUG_EXISTED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivatingHidesFromPublicButAdminStillSees() {
        var created = admin.createProduct(product("seasonal", null));
        admin.deactivateProduct(created.id());

        assertThat(publicService.search("", "seasonal", 0, 20).items()).isEmpty();
        assertThat(admin.listProducts(0, 50).items()).extracting(ProductSummaryResponse::slug).contains("seasonal");
        assertThat(admin.getProduct(created.id()).active()).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void variantCanBeDeactivatedAndUpdated() {
        var created = admin.createProduct(product("polo", null));
        var v = admin.createVariant(created.id(), new VariantRequest("M", "green", "POLO-M-G", 4, null, null));
        var updated = admin.updateVariant(v.id(), new VariantRequest("M", "green", "POLO-M-G", 9, 199_000L, null));
        assertThat(updated.stock()).isEqualTo(9);
        assertThat(updated.price()).isEqualTo(199_000);

        admin.deactivateVariant(v.id());
        assertThat(publicService.getBySlug("polo").variants()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminIsDenied() {
        assertThatThrownBy(() -> admin.createCategory(new CategoryRequest("X", "x")))
                .isInstanceOf(AccessDeniedException.class);
    }
}
```

`controller/AdminSecurityTest.java`:
```java
package com.example.identifyservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/admin/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void plainUserGets403() throws Exception {
        mvc.perform(get("/admin/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGets200() throws Exception {
        mvc.perform(get("/admin/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `mvn -q test -Dtest=AdminCatalogServiceTest,AdminSecurityTest`
Expected: compilation error, `AdminCatalogService` missing.

- [ ] **Step 4: Implement `AdminCatalogService`**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CategoryRequest;
import com.example.identifyservice.dto.request.ProductRequest;
import com.example.identifyservice.dto.request.VariantRequest;
import com.example.identifyservice.dto.response.CategoryResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.dto.response.VariantResponse;
import com.example.identifyservice.entity.Category;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.CategoryRepository;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogService {
    CategoryRepository categoryRepository;
    ProductRepository productRepository;
    ProductVariantRepository variantRepository;

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) throw new AppException(ErrorCode.SLUG_EXISTED);
        return CategoryResponse.from(categoryRepository.save(
                Category.builder().name(request.name().trim()).slug(request.slug()).build()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> listProducts(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(productRepository.findAll(pageable).map(ProductSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(String id) {
        return ProductDetailResponse.from(requireProduct(id), false);
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySlug(request.slug())) throw new AppException(ErrorCode.SLUG_EXISTED);
        Product product = Product.builder().build();
        apply(product, request);
        return ProductDetailResponse.from(productRepository.save(product), false);
    }

    @Transactional
    public ProductDetailResponse updateProduct(String id, ProductRequest request) {
        Product product = requireProduct(id);
        if (!product.getSlug().equals(request.slug()) && productRepository.existsBySlug(request.slug()))
            throw new AppException(ErrorCode.SLUG_EXISTED);
        apply(product, request);
        return ProductDetailResponse.from(productRepository.save(product), false);
    }

    @Transactional
    public void deactivateProduct(String id) {
        Product product = requireProduct(id);
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional
    public VariantResponse createVariant(String productId, VariantRequest request) {
        Product product = requireProduct(productId);
        if (variantRepository.existsBySku(request.sku())) throw new AppException(ErrorCode.SKU_EXISTED);
        ProductVariant variant = ProductVariant.builder().product(product).build();
        apply(variant, request);
        product.getVariants().add(variant);
        return VariantResponse.from(variantRepository.save(variant));
    }

    @Transactional
    public VariantResponse updateVariant(String variantId, VariantRequest request) {
        ProductVariant variant = requireVariant(variantId);
        if (!variant.getSku().equals(request.sku()) && variantRepository.existsBySku(request.sku()))
            throw new AppException(ErrorCode.SKU_EXISTED);
        apply(variant, request);
        return VariantResponse.from(variantRepository.save(variant));
    }

    @Transactional
    public void deactivateVariant(String variantId) {
        ProductVariant variant = requireVariant(variantId);
        variant.setActive(false);
        variantRepository.save(variant);
    }

    private void apply(Product product, ProductRequest r) {
        product.setName(r.name().trim());
        product.setSlug(r.slug());
        product.setDescription(r.description());
        product.setBasePrice(r.basePrice());
        product.setImageUrl(r.imageUrl());
        product.setActive(r.active() == null || r.active());
        product.setCategory(r.categoryId() == null || r.categoryId().isBlank() ? null
                : categoryRepository.findById(r.categoryId())
                        .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND)));
    }

    private void apply(ProductVariant v, VariantRequest r) {
        v.setSize(r.size().trim());
        v.setColor(r.color().trim());
        v.setSku(r.sku().trim());
        v.setStock(r.stock());
        v.setPrice(r.price());
        v.setActive(r.active() == null || r.active());
    }

    private Product requireProduct(String id) {
        return productRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductVariant requireVariant(String id) {
        return variantRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
    }
}
```

- [ ] **Step 5: Implement `AdminCatalogController`**

```java
package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.CategoryRequest;
import com.example.identifyservice.dto.request.ProductRequest;
import com.example.identifyservice.dto.request.VariantRequest;
import com.example.identifyservice.dto.response.CategoryResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.dto.response.VariantResponse;
import com.example.identifyservice.service.AdminCatalogService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminCatalogController {
    AdminCatalogService service;

    @PostMapping("/categories")
    ApiResponse<CategoryResponse> createCategory(@RequestBody @Valid CategoryRequest request) {
        return ApiResponse.ok(service.createCategory(request));
    }

    @GetMapping("/products")
    ApiResponse<PageResponse<ProductSummaryResponse>> listProducts(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listProducts(page, size));
    }

    @GetMapping("/products/{id}")
    ApiResponse<ProductDetailResponse> getProduct(@PathVariable String id) {
        return ApiResponse.ok(service.getProduct(id));
    }

    @PostMapping("/products")
    ApiResponse<ProductDetailResponse> createProduct(@RequestBody @Valid ProductRequest request) {
        return ApiResponse.ok(service.createProduct(request));
    }

    @PutMapping("/products/{id}")
    ApiResponse<ProductDetailResponse> updateProduct(@PathVariable String id, @RequestBody @Valid ProductRequest request) {
        return ApiResponse.ok(service.updateProduct(id, request));
    }

    @DeleteMapping("/products/{id}")
    ApiResponse<Void> deactivateProduct(@PathVariable String id) {
        service.deactivateProduct(id);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/products/{productId}/variants")
    ApiResponse<VariantResponse> createVariant(@PathVariable String productId, @RequestBody @Valid VariantRequest request) {
        return ApiResponse.ok(service.createVariant(productId, request));
    }

    @PutMapping("/variants/{id}")
    ApiResponse<VariantResponse> updateVariant(@PathVariable String id, @RequestBody @Valid VariantRequest request) {
        return ApiResponse.ok(service.updateVariant(id, request));
    }

    @DeleteMapping("/variants/{id}")
    ApiResponse<Void> deactivateVariant(@PathVariable String id) {
        service.deactivateVariant(id);
        return ApiResponse.<Void>builder().build();
    }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `mvn -q test -Dtest=AdminCatalogServiceTest,AdminSecurityTest`
Expected: 8 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "feat: admin catalog CRUD with soft delete"
```

---

### Task 7: Shipping fee by province

**Files:**
- Create: `entity/ShippingRate.java`, `repository/ShippingRateRepository.java`, `util/VietnamProvinces.java`, `configuration/ShippingRateSeeder.java`, `dto/request/ShippingRateRequest.java`, `dto/response/ShippingRateResponse.java`, `dto/response/ShippingFeeResponse.java`, `service/ShippingService.java`, `controller/ShippingController.java`
- Test: `service/ShippingServiceTest.java`

**Interfaces:**
- Produces: `ShippingService.requireRate(String province) : ShippingRate` (throws `INVALID_PROVINCE`), `fee(String) : ShippingFeeResponse`, `list() : List<ShippingRateResponse>`, admin `create(ShippingRateRequest)`, `updateFee(String id, long fee)`. Public: `GET /shipping/fee?province=`, `GET /shipping/provinces`. Admin: `GET/POST /admin/shipping-rates`, `PUT /admin/shipping-rates/{id}`.
- Note: seeded with the **34 provincial-level units in force since July 2025** (the spec said 63, which is the pre-merger count). Admins can add rows via `POST /admin/shipping-rates`.

- [ ] **Step 1: Create the entity and repository**

`entity/ShippingRate.java`:
```java
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
@Table(name = "shipping_rate")
public class ShippingRate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, unique = true, length = 100)
    String province;

    @Column(nullable = false)
    long fee;
}
```

`repository/ShippingRateRepository.java`:
```java
package com.example.identifyservice.repository;

import com.example.identifyservice.entity.ShippingRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShippingRateRepository extends JpaRepository<ShippingRate, String> {
    Optional<ShippingRate> findByProvinceIgnoreCase(String province);
    boolean existsByProvinceIgnoreCase(String province);
    List<ShippingRate> findAllByOrderByProvinceAsc();
}
```

- [ ] **Step 2: Create the province list** `util/VietnamProvinces.java`

```java
package com.example.identifyservice.util;

import java.util.List;

/** The 34 provincial-level units of Vietnam (in force since 1 July 2025). */
public final class VietnamProvinces {
    private VietnamProvinces() {}

    public static final List<String> ALL = List.of(
            "Hà Nội", "TP Hồ Chí Minh", "Hải Phòng", "Đà Nẵng", "Cần Thơ", "Huế",
            "Tuyên Quang", "Lào Cai", "Thái Nguyên", "Phú Thọ", "Bắc Ninh", "Hưng Yên",
            "Ninh Bình", "Quảng Trị", "Quảng Ngãi", "Gia Lai", "Khánh Hòa", "Lâm Đồng",
            "Đắk Lắk", "Đồng Nai", "Tây Ninh", "Vĩnh Long", "Đồng Tháp", "Cà Mau",
            "An Giang", "Cao Bằng", "Điện Biên", "Hà Tĩnh", "Lai Châu", "Lạng Sơn",
            "Nghệ An", "Quảng Ninh", "Thanh Hóa", "Sơn La");
}
```

- [ ] **Step 3: Create the seeder** `configuration/ShippingRateSeeder.java`

```java
package com.example.identifyservice.configuration;

import com.example.identifyservice.entity.ShippingRate;
import com.example.identifyservice.repository.ShippingRateRepository;
import com.example.identifyservice.util.VietnamProvinces;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShippingRateSeeder implements ApplicationRunner {
    private static final long METRO_FEE = 25_000;
    private static final long DEFAULT_FEE = 35_000;

    private final ShippingRateRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        for (String province : VietnamProvinces.ALL) {
            if (!repository.existsByProvinceIgnoreCase(province)) {
                boolean metro = province.equals("Hà Nội") || province.equals("TP Hồ Chí Minh");
                repository.save(ShippingRate.builder().province(province)
                        .fee(metro ? METRO_FEE : DEFAULT_FEE).build());
            }
        }
    }
}
```

- [ ] **Step 4: Create the DTO records**

`dto/request/ShippingRateRequest.java`:
```java
package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingRateRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 100, message = "INVALID_INPUT") String province,
        @Min(value = 0, message = "INVALID_INPUT") long fee) {
}
```

`dto/response/ShippingRateResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.ShippingRate;

public record ShippingRateResponse(String id, String province, long fee) {
    public static ShippingRateResponse from(ShippingRate r) {
        return new ShippingRateResponse(r.getId(), r.getProvince(), r.getFee());
    }
}
```

`dto/response/ShippingFeeResponse.java`:
```java
package com.example.identifyservice.dto.response;

public record ShippingFeeResponse(String province, long fee) {
}
```

- [ ] **Step 5: Write the failing test** `service/ShippingServiceTest.java`

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.ShippingRateRequest;
import com.example.identifyservice.dto.response.ShippingRateResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShippingServiceTest {
    @Autowired ShippingService shipping;

    @Test
    void allProvincesAreSeeded() {
        assertThat(shipping.list()).hasSize(34);
        assertThat(shipping.list()).extracting(ShippingRateResponse::province).contains("Hà Nội", "Đà Nẵng");
    }

    @Test
    void lookupIgnoresCaseAndSurroundingSpaces() {
        assertThat(shipping.fee("  hà nội ").fee()).isEqualTo(25_000);
        assertThat(shipping.fee("Lào Cai").fee()).isEqualTo(35_000);
    }

    @Test
    void unknownOrBlankProvinceIsRejected() {
        for (String p : new String[]{"Atlantis", "", "   ", null}) {
            assertThatThrownBy(() -> shipping.requireRate(p))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_PROVINCE);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanChangeFeeAndAddProvince() {
        var hanoi = shipping.list().stream().filter(r -> r.province().equals("Hà Nội")).findFirst().orElseThrow();
        shipping.updateFee(hanoi.id(), 30_000);
        assertThat(shipping.fee("Hà Nội").fee()).isEqualTo(30_000);

        shipping.create(new ShippingRateRequest("Khu Test", 50_000));
        assertThat(shipping.fee("khu test").fee()).isEqualTo(50_000);
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminCannotEditRates() {
        assertThatThrownBy(() -> shipping.create(new ShippingRateRequest("X", 1)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
```

- [ ] **Step 6: Run to verify failure**

Run: `mvn -q test -Dtest=ShippingServiceTest`
Expected: compilation error, `ShippingService` missing.

- [ ] **Step 7: Implement `ShippingService`**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.ShippingRateRequest;
import com.example.identifyservice.dto.response.ShippingFeeResponse;
import com.example.identifyservice.dto.response.ShippingRateResponse;
import com.example.identifyservice.entity.ShippingRate;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.ShippingRateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShippingService {
    ShippingRateRepository repository;

    @Transactional(readOnly = true)
    public ShippingRate requireRate(String province) {
        if (province == null || province.isBlank()) throw new AppException(ErrorCode.INVALID_PROVINCE);
        return repository.findByProvinceIgnoreCase(province.trim())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_PROVINCE));
    }

    @Transactional(readOnly = true)
    public ShippingFeeResponse fee(String province) {
        ShippingRate rate = requireRate(province);
        return new ShippingFeeResponse(rate.getProvince(), rate.getFee());
    }

    @Transactional(readOnly = true)
    public List<ShippingRateResponse> list() {
        return repository.findAllByOrderByProvinceAsc().stream().map(ShippingRateResponse::from).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ShippingRateResponse create(ShippingRateRequest request) {
        if (repository.existsByProvinceIgnoreCase(request.province().trim()))
            throw new AppException(ErrorCode.SLUG_EXISTED);
        return ShippingRateResponse.from(repository.save(
                ShippingRate.builder().province(request.province().trim()).fee(request.fee()).build()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ShippingRateResponse updateFee(String id, long fee) {
        if (fee < 0) throw new AppException(ErrorCode.INVALID_INPUT);
        ShippingRate rate = repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.INVALID_PROVINCE));
        rate.setFee(fee);
        return ShippingRateResponse.from(repository.save(rate));
    }
}
```

- [ ] **Step 8: Implement `ShippingController`**

```java
package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.ShippingRateRequest;
import com.example.identifyservice.dto.response.ShippingFeeResponse;
import com.example.identifyservice.dto.response.ShippingRateResponse;
import com.example.identifyservice.service.ShippingService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShippingController {
    ShippingService shippingService;

    @GetMapping("/shipping/fee")
    ApiResponse<ShippingFeeResponse> fee(@RequestParam String province) {
        return ApiResponse.ok(shippingService.fee(province));
    }

    @GetMapping("/shipping/provinces")
    ApiResponse<List<ShippingRateResponse>> provinces() {
        return ApiResponse.ok(shippingService.list());
    }

    @GetMapping("/admin/shipping-rates")
    ApiResponse<List<ShippingRateResponse>> adminList() {
        return ApiResponse.ok(shippingService.list());
    }

    @PostMapping("/admin/shipping-rates")
    ApiResponse<ShippingRateResponse> adminCreate(@RequestBody @Valid ShippingRateRequest request) {
        return ApiResponse.ok(shippingService.create(request));
    }

    @PutMapping("/admin/shipping-rates/{id}")
    ApiResponse<ShippingRateResponse> adminUpdate(@PathVariable String id, @RequestBody @Valid ShippingRateRequest request) {
        return ApiResponse.ok(shippingService.updateFee(id, request.fee()));
    }
}
```

- [ ] **Step 9: Run to verify it passes**

Run: `mvn -q test -Dtest=ShippingServiceTest`
Expected: 5 tests PASS.

- [ ] **Step 10: Run the whole suite and commit**

Run: `mvn -q test`
Expected: all PASS.

```bash
git add src
git commit -m "feat: shipping fee by province with admin-editable rates"
```
