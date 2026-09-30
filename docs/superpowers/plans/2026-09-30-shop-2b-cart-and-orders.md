# Clothing Shop - Plan 2b: Cart and Orders

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Server-side cart, checkout that computes prices from the database and reserves stock atomically, my-orders and admin order management. COD works end to end; MoMo orders are created here as `PENDING_PAYMENT` and paid in Plan 3.

**Architecture:** `CartService` and `OrderService` in the existing service layer. Stock is reserved with a conditional `UPDATE ... WHERE stock >= :qty` so concurrent checkouts cannot oversell. Order state changes that must happen once are conditional UPDATEs (used by Plan 3). Domain events (`OrderConfirmedEvent`) decouple email sending (Plan 3).

**Tech Stack:** Spring Boot 3.2.3, Spring Data JPA, JUnit 5 + H2.

**Spec:** `docs/superpowers/specs/2026-09-30-clothing-shop-design.md` (sections 3, 4, 5)

**Prerequisite:** Plan 2a complete.

## Global Constraints

- Same as Plan 2a (records, `ApiResponse`, `AppException`, `Test` suffix, UUID ids, `long` VND, commit trailer).
- Table for orders is `orders` and the JPA entity name is `ShopOrder` (`Order` is a reserved word in HQL).
- MoMo orders expire after `shop.order-expiry-minutes` (15) via `expiresAt`; COD orders have `expiresAt = null`.
- Prices are always read from the database at checkout, never from the client.

## Review Focus

- Two users buying the last unit at the same time: exactly one succeeds, stock ends at 0 (Task 9 concurrency test).
- A checkout that fails on its second line must roll back the first line's stock reservation (Task 9 test).
- Product or variant deactivated after being put in a cart must block checkout (Task 9 test).
- Someone else's order code must look like "not found" (Task 9 test).
- Cart quantity 0, negative, above 99, or above stock is rejected (Task 8 test).
- Admin cancel restocks; illegal status jumps are rejected (Task 9 tests).

(Paths are under `src/main/java/com/example/identifyservice/` for main and `src/test/java/com/example/identifyservice/` for tests.)

---

### Task 8: Cart

**Files:**
- Create: `entity/Cart.java`, `entity/CartItem.java`, `repository/CartRepository.java`, `service/CurrentUserService.java`, `service/CartService.java`, `dto/request/CartItemRequest.java`, `dto/response/CartItemResponse.java`, `dto/response/CartResponse.java`, `controller/CartController.java`
- Test: `service/CartServiceTest.java`

**Interfaces:**
- Produces:
  - `CurrentUserService.requireUser() : User` (throws `UNAUTHENTICATED` when anonymous, unknown, or not ACTIVE); `CurrentUserService.isAdmin() : boolean`
  - `CartService.getCart()`, `addItem(String variantId, int qty)`, `updateItem(String variantId, int qty)`, `removeItem(String variantId)`, `clear()`, each returning `CartResponse`
  - `CartResponse(List<CartItemResponse> items, long subtotal, int totalQuantity)`; `CartItemResponse(String variantId, String productSlug, String productName, String imageUrl, String size, String color, long unitPrice, int quantity, long lineTotal, int stock, boolean available)`
  - `CartRepository.findByUser(User) : Optional<Cart>`
  - Endpoints (authenticated): `GET /cart`, `POST /cart/items`, `PUT /cart/items/{variantId}`, `DELETE /cart/items/{variantId}`, `DELETE /cart`

- [ ] **Step 1: Create entities and repository**

`entity/Cart.java`:
```java
package com.example.identifyservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "cart", uniqueConstraints = @UniqueConstraint(name = "uk_cart_user", columnNames = "user_id"))
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<CartItem> items = new ArrayList<>();
}
```

`entity/CartItem.java`:
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
@Table(name = "cart_item", uniqueConstraints =
        @UniqueConstraint(name = "uk_cart_variant", columnNames = {"cart_id", "variant_id"}))
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id")
    Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id")
    ProductVariant variant;

    @Column(nullable = false)
    int quantity;
}
```

`repository/CartRepository.java`:
```java
package com.example.identifyservice.repository;

import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {
    Optional<Cart> findByUser(User user);
}
```

- [ ] **Step 2: Create `CurrentUserService`**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.entity.User;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CurrentUserService {
    UserRepository userRepository;

    public User requireUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (!"ACTIVE".equals(user.getStatus())) throw new AppException(ErrorCode.UNAUTHENTICATED);
        return user;
    }

    public boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
```

- [ ] **Step 3: Create the DTOs**

`dto/request/CartItemRequest.java`:
```java
package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CartItemRequest(@NotBlank(message = "INVALID_INPUT") String variantId, int quantity) {
}
```

`dto/response/CartItemResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;

public record CartItemResponse(String variantId, String productSlug, String productName, String imageUrl,
                               String size, String color, long unitPrice, int quantity, long lineTotal,
                               int stock, boolean available) {
    public static CartItemResponse from(CartItem item) {
        ProductVariant v = item.getVariant();
        Product p = v.getProduct();
        long unit = v.effectivePrice();
        return new CartItemResponse(v.getId(), p.getSlug(), p.getName(), p.getImageUrl(), v.getSize(), v.getColor(),
                unit, item.getQuantity(), unit * item.getQuantity(), v.getStock(),
                v.isActive() && p.isActive() && v.getStock() >= item.getQuantity());
    }
}
```

`dto/response/CartResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Cart;

import java.util.List;

public record CartResponse(List<CartItemResponse> items, long subtotal, int totalQuantity) {
    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(CartItemResponse::from).toList();
        return new CartResponse(items, items.stream().mapToLong(CartItemResponse::lineTotal).sum(),
                items.stream().mapToInt(CartItemResponse::quantity).sum());
    }
}
```

- [ ] **Step 4: Write the failing test** `service/CartServiceTest.java`

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.CartResponse;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "alice", roles = "USER")
class CartServiceTest {
    @Autowired CartService cart;
    @Autowired TestDataFactory data;
    @Autowired ProductRepository products;

    Product tee;
    ProductVariant m;
    ProductVariant l;

    @BeforeEach
    void setUp() {
        data.user("alice");
        tee = data.product("cart-tee", 200_000, true);
        m = data.variant(tee, "M", "white", 5, null);
        l = data.variant(tee, "L", "white", 2, 220_000L);
    }

    private void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    void addingSameVariantTwiceMergesQuantity() {
        cart.addItem(m.getId(), 1);
        CartResponse r = cart.addItem(m.getId(), 2);
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).quantity()).isEqualTo(3);
        assertThat(r.subtotal()).isEqualTo(600_000);
    }

    @Test
    void subtotalUsesVariantPriceOverride() {
        cart.addItem(m.getId(), 1);
        CartResponse r = cart.addItem(l.getId(), 1);
        assertThat(r.subtotal()).isEqualTo(200_000 + 220_000);
        assertThat(r.totalQuantity()).isEqualTo(2);
    }

    @Test
    void rejectsBadQuantities() {
        for (int q : new int[]{0, -1, 100}) {
            assertError(() -> cart.addItem(m.getId(), q), ErrorCode.INVALID_QUANTITY);
        }
        assertError(() -> cart.addItem(l.getId(), 3), ErrorCode.OUT_OF_STOCK);
    }

    @Test
    void mergedQuantityCannotExceedStock() {
        cart.addItem(l.getId(), 2);
        assertError(() -> cart.addItem(l.getId(), 1), ErrorCode.OUT_OF_STOCK);
    }

    @Test
    void inactiveVariantOrProductCannotBeAdded() {
        m.setActive(false);
        assertError(() -> cart.addItem(m.getId(), 1), ErrorCode.VARIANT_NOT_FOUND);
        tee.setActive(false);
        assertError(() -> cart.addItem(l.getId(), 1), ErrorCode.VARIANT_NOT_FOUND);
        assertError(() -> cart.addItem("no-such-variant", 1), ErrorCode.VARIANT_NOT_FOUND);
    }

    @Test
    void updateRemoveAndClear() {
        cart.addItem(m.getId(), 1);
        cart.addItem(l.getId(), 1);
        assertThat(cart.updateItem(m.getId(), 4).items())
                .filteredOn(i -> i.variantId().equals(m.getId())).extracting(i -> i.quantity()).containsExactly(4);
        assertThat(cart.removeItem(l.getId()).items()).hasSize(1);
        assertError(() -> cart.updateItem(l.getId(), 1), ErrorCode.VARIANT_NOT_FOUND);
        assertThat(cart.clear().items()).isEmpty();
    }

    @Test
    void cartsAreIsolatedPerUser() {
        cart.addItem(m.getId(), 1);
        data.user("bob");
        var bob = new org.springframework.security.authentication.TestingAuthenticationToken("bob", "x", "ROLE_USER");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(bob);
        assertThat(cart.getCart().items()).isEmpty();
    }
}
```

- [ ] **Step 5: Run to verify failure**

Run: `mvn -q test -Dtest=CartServiceTest`
Expected: compilation error, `CartService` missing.

- [ ] **Step 6: Implement `CartService`**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.CartResponse;
import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.CartRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartService {
    static final int MAX_QUANTITY = 99;

    CartRepository cartRepository;
    ProductVariantRepository variantRepository;
    CurrentUserService currentUserService;

    @Transactional
    public CartResponse getCart() {
        return CartResponse.from(getOrCreate());
    }

    @Transactional
    public CartResponse addItem(String variantId, int quantity) {
        checkQuantityRange(quantity);
        Cart cart = getOrCreate();
        ProductVariant variant = requireBuyable(variantId);
        CartItem item = find(cart, variantId).orElseGet(() -> {
            CartItem created = CartItem.builder().cart(cart).variant(variant).quantity(0).build();
            cart.getItems().add(created);
            return created;
        });
        int merged = item.getQuantity() + quantity;
        checkQuantity(merged, variant);
        item.setQuantity(merged);
        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItem(String variantId, int quantity) {
        checkQuantityRange(quantity);
        Cart cart = getOrCreate();
        CartItem item = find(cart, variantId).orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
        checkQuantity(quantity, requireBuyable(variantId));
        item.setQuantity(quantity);
        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItem(String variantId) {
        Cart cart = getOrCreate();
        cart.getItems().removeIf(i -> i.getVariant().getId().equals(variantId));
        return CartResponse.from(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse clear() {
        Cart cart = getOrCreate();
        cart.getItems().clear();
        return CartResponse.from(cartRepository.save(cart));
    }

    private Cart getOrCreate() {
        var user = currentUserService.requireUser();
        return cartRepository.findByUser(user)
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));
    }

    private Optional<CartItem> find(Cart cart, String variantId) {
        return cart.getItems().stream().filter(i -> i.getVariant().getId().equals(variantId)).findFirst();
    }

    private ProductVariant requireBuyable(String variantId) {
        ProductVariant v = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
        if (!v.isActive() || !v.getProduct().isActive()) throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
        return v;
    }

    private void checkQuantityRange(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) throw new AppException(ErrorCode.INVALID_QUANTITY);
    }

    private void checkQuantity(int quantity, ProductVariant variant) {
        if (quantity > MAX_QUANTITY) throw new AppException(ErrorCode.INVALID_QUANTITY);
        if (quantity > variant.getStock()) throw new AppException(ErrorCode.OUT_OF_STOCK);
    }
}
```

- [ ] **Step 7: Implement `CartController`**

```java
package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.CartItemRequest;
import com.example.identifyservice.dto.response.CartResponse;
import com.example.identifyservice.service.CartService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartController {
    CartService cartService;

    @GetMapping
    ApiResponse<CartResponse> get() {
        return ApiResponse.ok(cartService.getCart());
    }

    @PostMapping("/items")
    ApiResponse<CartResponse> add(@RequestBody @Valid CartItemRequest request) {
        return ApiResponse.ok(cartService.addItem(request.variantId(), request.quantity()));
    }

    @PutMapping("/items/{variantId}")
    ApiResponse<CartResponse> update(@PathVariable String variantId, @RequestBody CartItemRequest request) {
        return ApiResponse.ok(cartService.updateItem(variantId, request.quantity()));
    }

    @DeleteMapping("/items/{variantId}")
    ApiResponse<CartResponse> remove(@PathVariable String variantId) {
        return ApiResponse.ok(cartService.removeItem(variantId));
    }

    @DeleteMapping
    ApiResponse<CartResponse> clear() {
        return ApiResponse.ok(cartService.clear());
    }
}
```

- [ ] **Step 8: Run to verify it passes**

Run: `mvn -q test -Dtest=CartServiceTest`
Expected: 7 tests PASS.

- [ ] **Step 9: Commit**

```bash
git add src
git commit -m "feat: server-side cart"
```

---

### Task 9: Orders, checkout, admin order management

**Files:**
- Create: `enums/PaymentMethod.java`, `enums/OrderStatus.java`, `enums/PaymentStatus.java`, `entity/Order.java`, `entity/OrderItem.java`, `repository/OrderRepository.java`, `event/OrderConfirmedEvent.java`, `dto/request/CheckoutRequest.java`, `dto/response/OrderItemResponse.java`, `dto/response/OrderResponse.java`, `service/OrderService.java`, `controller/OrderController.java`, `controller/AdminOrderController.java`
- Test: `enums/OrderStatusTest.java`, `service/OrderServiceTest.java`, `service/OrderConcurrencyTest.java`

**Interfaces:**
- Consumes: `CartService`, `CurrentUserService`, `ShippingService.requireRate`, `ProductVariantRepository.decrementStock/incrementStock`.
- Produces:
  - Enums: `PaymentMethod {COD, MOMO}`; `OrderStatus {PENDING_PAYMENT, PENDING_CONFIRM, CONFIRMED, SHIPPING, COMPLETED, CANCELLED}` with `canTransitionTo(OrderStatus)`; `PaymentStatus {UNPAID, PAID, FAILED, EXPIRED}`
  - `CheckoutRequest(String receiverName, String phone, String email, String address, String province, String note, PaymentMethod paymentMethod)`
  - `OrderService.checkout(CheckoutRequest) : OrderResponse`, `myOrders(int page, int size) : PageResponse<OrderResponse>`, `getMyOrder(String code) : OrderResponse`, `requireOwnedOrder(String code) : Order`, `adminList(OrderStatus status, int page, int size)`, `adminUpdateStatus(String code, OrderStatus to) : OrderResponse`, `cancelPendingPayment(String orderId, PaymentStatus finalPaymentStatus) : boolean`
  - `OrderRepository.markPaid(String id, Instant now) : int`, `cancelPending(String id, PaymentStatus ps) : int`, `findByCode`, `findByUser(User, Pageable)`, `findByStatus(OrderStatus, Pageable)`, `findByStatusAndExpiresAtBefore(OrderStatus, Instant)`
  - `OrderConfirmedEvent(String orderId)` published when a COD order is placed and (Plan 3) when a MoMo order becomes paid
  - `OrderResponse(String code, OrderStatus status, PaymentMethod paymentMethod, PaymentStatus paymentStatus, long subtotal, long shippingFee, long total, String receiverName, String phone, String email, String address, String province, String note, Instant createdAt, Instant paidAt, Instant expiresAt, List<OrderItemResponse> items)`; `OrderItemResponse(String productName, String size, String color, long unitPrice, int quantity, long lineTotal)`
  - Endpoints: `POST /orders`, `GET /orders`, `GET /orders/{code}`; admin `GET /admin/orders?status=`, `PUT /admin/orders/{code}/status`

- [ ] **Step 1: Create the enums**

`enums/PaymentMethod.java`:
```java
package com.example.identifyservice.enums;

public enum PaymentMethod {
    COD, MOMO
}
```

`enums/PaymentStatus.java`:
```java
package com.example.identifyservice.enums;

public enum PaymentStatus {
    UNPAID, PAID, FAILED, EXPIRED
}
```

`enums/OrderStatus.java`:
```java
package com.example.identifyservice.enums;

public enum OrderStatus {
    PENDING_PAYMENT, PENDING_CONFIRM, CONFIRMED, SHIPPING, COMPLETED, CANCELLED;

    /** Manual (admin) transitions. PENDING_PAYMENT -> PENDING_CONFIRM happens only through payment. */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING_PAYMENT -> next == CANCELLED;
            case PENDING_CONFIRM -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPING || next == CANCELLED;
            case SHIPPING -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
```

- [ ] **Step 2: Write the failing enum test** `enums/OrderStatusTest.java`

```java
package com.example.identifyservice.enums;

import org.junit.jupiter.api.Test;

import static com.example.identifyservice.enums.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {
    @Test
    void allowedTransitions() {
        assertThat(PENDING_CONFIRM.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(SHIPPING)).isTrue();
        assertThat(SHIPPING.canTransitionTo(COMPLETED)).isTrue();
        assertThat(PENDING_CONFIRM.canTransitionTo(CANCELLED)).isTrue();
        assertThat(PENDING_PAYMENT.canTransitionTo(CANCELLED)).isTrue();
    }

    @Test
    void forbiddenTransitions() {
        assertThat(PENDING_CONFIRM.canTransitionTo(COMPLETED)).isFalse();
        assertThat(PENDING_PAYMENT.canTransitionTo(CONFIRMED)).isFalse();
        assertThat(SHIPPING.canTransitionTo(CANCELLED)).isFalse();
        assertThat(COMPLETED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(CANCELLED.canTransitionTo(CONFIRMED)).isFalse();
    }
}
```

Run: `mvn -q test -Dtest=OrderStatusTest`
Expected: PASS (enums already written; this pins the state machine).

- [ ] **Step 3: Create the entities**

`entity/Order.java`:
```java
package com.example.identifyservice.entity;

import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
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
```

`entity/OrderItem.java`:
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
}
```

- [ ] **Step 4: Create `OrderRepository`**

```java
package com.example.identifyservice.repository;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findByCode(String code);
    Page<Order> findByUser(User user, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant time);

    /** Atomic "pay once": succeeds only while the order is still PENDING_PAYMENT. Returns rows changed (0 or 1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ShopOrder o
            set o.status = com.example.identifyservice.enums.OrderStatus.PENDING_CONFIRM,
                o.paymentStatus = com.example.identifyservice.enums.PaymentStatus.PAID,
                o.paidAt = :now
            where o.id = :id
              and o.status = com.example.identifyservice.enums.OrderStatus.PENDING_PAYMENT
            """)
    int markPaid(@Param("id") String id, @Param("now") Instant now);

    /** Atomic "close unpaid order": succeeds only while the order is still PENDING_PAYMENT. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ShopOrder o
            set o.status = com.example.identifyservice.enums.OrderStatus.CANCELLED,
                o.paymentStatus = :paymentStatus
            where o.id = :id
              and o.status = com.example.identifyservice.enums.OrderStatus.PENDING_PAYMENT
            """)
    int cancelPending(@Param("id") String id, @Param("paymentStatus") PaymentStatus paymentStatus);
}
```

- [ ] **Step 5: Create the event, request and response records**

`event/OrderConfirmedEvent.java`:
```java
package com.example.identifyservice.event;

/** Published once an order is confirmed for the customer (COD placed, or MoMo paid). */
public record OrderConfirmedEvent(String orderId) {
}
```

`dto/request/CheckoutRequest.java`:
```java
package com.example.identifyservice.dto.request;

import com.example.identifyservice.enums.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 100, message = "INVALID_INPUT") String receiverName,
        @NotBlank(message = "INVALID_INPUT") @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "INVALID_INPUT") String phone,
        @NotBlank(message = "INVALID_INPUT") @Email(message = "INVALID_INPUT") @Size(max = 150, message = "INVALID_INPUT") String email,
        @NotBlank(message = "INVALID_INPUT") @Size(max = 300, message = "INVALID_INPUT") String address,
        @NotBlank(message = "INVALID_INPUT") String province,
        @Size(max = 500, message = "INVALID_INPUT") String note,
        @NotNull(message = "INVALID_INPUT") PaymentMethod paymentMethod) {
}
```

`dto/response/OrderItemResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.OrderItem;

public record OrderItemResponse(String productName, String size, String color, long unitPrice, int quantity,
                                long lineTotal) {
    public static OrderItemResponse from(OrderItem i) {
        return new OrderItemResponse(i.getProductName(), i.getSize(), i.getColor(), i.getUnitPrice(),
                i.getQuantity(), i.getUnitPrice() * i.getQuantity());
    }
}
```

`dto/response/OrderResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponse(String code, OrderStatus status, PaymentMethod paymentMethod, PaymentStatus paymentStatus,
                            long subtotal, long shippingFee, long total, String receiverName, String phone,
                            String email, String address, String province, String note, Instant createdAt,
                            Instant paidAt, Instant expiresAt, List<OrderItemResponse> items) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getCode(), o.getStatus(), o.getPaymentMethod(), o.getPaymentStatus(),
                o.getSubtotal(), o.getShippingFee(), o.getTotal(), o.getReceiverName(), o.getPhone(), o.getEmail(),
                o.getAddress(), o.getProvince(), o.getNote(), o.getCreatedAt(), o.getPaidAt(), o.getExpiresAt(),
                o.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
```

- [ ] **Step 6: Write the failing service test** `service/OrderServiceTest.java`

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "alice", roles = "USER")
class OrderServiceTest {
    @Autowired OrderService orders;
    @Autowired CartService cart;
    @Autowired TestDataFactory data;
    @Autowired ProductVariantRepository variants;
    @Autowired EntityManager em;

    Product tee;
    ProductVariant m;

    @BeforeEach
    void setUp() {
        data.user("alice");
        data.user("bob");
        tee = data.product("basic-tee", 200_000, true);
        m = data.variant(tee, "M", "white", 5, null);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private CheckoutRequest request(PaymentMethod method, String province) {
        return new CheckoutRequest("Alice", "0901234567", "alice@example.com", "12 Nguyen Hue", province, null, method);
    }

    private void actAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(username, "x", role));
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((AppException) t).getErrorCode();
    }

    @Test
    void codCheckoutComputesTotalsFromDatabasePricesAndReservesStock() {
        cart.addItem(m.getId(), 2);
        OrderResponse order = orders.checkout(request(PaymentMethod.COD, "Hà Nội"));

        assertThat(order.subtotal()).isEqualTo(400_000);
        assertThat(order.shippingFee()).isEqualTo(25_000);
        assertThat(order.total()).isEqualTo(425_000);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.expiresAt()).isNull();
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).unitPrice()).isEqualTo(200_000);

        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(3);
        assertThat(cart.getCart().items()).isEmpty();
    }

    @Test
    void momoCheckoutIsPendingPaymentWithFifteenMinuteExpiry() {
        cart.addItem(m.getId(), 1);
        OrderResponse order = orders.checkout(request(PaymentMethod.MOMO, "TP Hồ Chí Minh"));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.expiresAt()).isBetween(Instant.now().plus(14, ChronoUnit.MINUTES),
                Instant.now().plus(16, ChronoUnit.MINUTES));
    }

    @Test
    void emptyCartUnknownProvinceAndInactiveProductAreRejected() {
        assertThatThrownBy(() -> orders.checkout(request(PaymentMethod.COD, "Hà Nội")))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.CART_EMPTY);

        cart.addItem(m.getId(), 1);
        assertThatThrownBy(() -> orders.checkout(request(PaymentMethod.COD, "Atlantis")))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_PROVINCE);

        tee.setActive(false);
        assertThatThrownBy(() -> orders.checkout(request(PaymentMethod.COD, "Hà Nội")))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.VARIANT_NOT_FOUND);
    }

    @Test
    void stockReducedAfterAddingToCartBlocksCheckout() {
        cart.addItem(m.getId(), 5);
        m.setStock(2);
        variants.save(m);
        em.flush();
        assertThatThrownBy(() -> orders.checkout(request(PaymentMethod.COD, "Hà Nội")))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    @Test
    void otherUsersOrderLooksNotFound() {
        cart.addItem(m.getId(), 1);
        String code = orders.checkout(request(PaymentMethod.COD, "Hà Nội")).code();
        assertThat(orders.getMyOrder(code).code()).isEqualTo(code);
        assertThat(orders.myOrders(0, 10).items()).extracting(OrderResponse::code).contains(code);

        actAs("bob", "ROLE_USER");
        assertThatThrownBy(() -> orders.getMyOrder(code))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThat(orders.myOrders(0, 10).items()).extracting(OrderResponse::code).doesNotContain(code);
    }

    @Test
    void adminMovesCodOrderThroughToCompletedAndItBecomesPaid() {
        cart.addItem(m.getId(), 1);
        String code = orders.checkout(request(PaymentMethod.COD, "Hà Nội")).code();

        actAs("admin", "ROLE_ADMIN");
        assertThat(orders.adminUpdateStatus(code, OrderStatus.CONFIRMED).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orders.adminUpdateStatus(code, OrderStatus.SHIPPING).status()).isEqualTo(OrderStatus.SHIPPING);
        OrderResponse done = orders.adminUpdateStatus(code, OrderStatus.COMPLETED);
        assertThat(done.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(done.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(done.paidAt()).isNotNull();
    }

    @Test
    void illegalTransitionIsRejected() {
        cart.addItem(m.getId(), 1);
        String code = orders.checkout(request(PaymentMethod.COD, "Hà Nội")).code();
        actAs("admin", "ROLE_ADMIN");
        assertThatThrownBy(() -> orders.adminUpdateStatus(code, OrderStatus.COMPLETED))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void adminCancelRestocks() {
        cart.addItem(m.getId(), 2);
        String code = orders.checkout(request(PaymentMethod.COD, "Hà Nội")).code();
        actAs("admin", "ROLE_ADMIN");
        orders.adminUpdateStatus(code, OrderStatus.CANCELLED);
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void cancelPendingPaymentRestocksOnlyOnce() {
        cart.addItem(m.getId(), 2);
        var order = orders.checkout(request(PaymentMethod.MOMO, "Hà Nội"));
        String orderId = orders.requireOwnedOrder(order.code()).getId();

        assertThat(orders.cancelPendingPayment(orderId, PaymentStatus.EXPIRED)).isTrue();
        assertThat(orders.cancelPendingPayment(orderId, PaymentStatus.EXPIRED)).isFalse();
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(orders.getMyOrder(order.code()).paymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void nonAdminCannotUseAdminOperations() {
        assertThatThrownBy(() -> orders.adminList(null, 0, 10))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
```

- [ ] **Step 7: Run to verify failure**

Run: `mvn -q test -Dtest=OrderServiceTest`
Expected: compilation error, `OrderService` missing.

- [ ] **Step 8: Implement `OrderService`**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.configuration.ShopProperties;
import com.example.identifyservice.entity.*;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.CartRepository;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter CODE_DATE =
            DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    OrderRepository orderRepository;
    CartRepository cartRepository;
    ProductVariantRepository variantRepository;
    ShippingService shippingService;
    CurrentUserService currentUserService;
    ShopProperties shopProperties;
    ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        User user = currentUserService.requireUser();
        Cart cart = cartRepository.findByUser(user).orElseThrow(() -> new AppException(ErrorCode.CART_EMPTY));
        if (cart.getItems().isEmpty()) throw new AppException(ErrorCode.CART_EMPTY);
        ShippingRate rate = shippingService.requireRate(request.province());

        Instant now = Instant.now();
        boolean momo = request.paymentMethod() == PaymentMethod.MOMO;
        Order order = Order.builder()
                .code(newCode(now))
                .user(user)
                .status(momo ? OrderStatus.PENDING_PAYMENT : OrderStatus.PENDING_CONFIRM)
                .paymentMethod(request.paymentMethod())
                .paymentStatus(PaymentStatus.UNPAID)
                .receiverName(request.receiverName().trim())
                .phone(request.phone().trim())
                .email(request.email().trim())
                .address(request.address().trim())
                .province(rate.getProvince())
                .note(request.note() == null || request.note().isBlank() ? null : request.note().trim())
                .shippingFee(rate.getFee())
                .expiresAt(momo ? now.plus(shopProperties.orderExpiryMinutes(), ChronoUnit.MINUTES) : null)
                .build();

        long subtotal = 0;
        for (CartItem cartItem : cart.getItems()) {
            ProductVariant variant = cartItem.getVariant();
            Product product = variant.getProduct();
            if (!variant.isActive() || !product.isActive()) throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
            if (variantRepository.decrementStock(variant.getId(), cartItem.getQuantity()) == 0)
                throw new AppException(ErrorCode.OUT_OF_STOCK);
            long unitPrice = variant.effectivePrice();
            subtotal += unitPrice * cartItem.getQuantity();
            order.getItems().add(OrderItem.builder().order(order).variantId(variant.getId())
                    .productName(product.getName()).size(variant.getSize()).color(variant.getColor())
                    .unitPrice(unitPrice).quantity(cartItem.getQuantity()).build());
        }
        order.setSubtotal(subtotal);
        order.setTotal(subtotal + rate.getFee());
        orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        if (!momo) eventPublisher.publishEvent(new OrderConfirmedEvent(order.getId()));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> myOrders(int page, int size) {
        User user = currentUserService.requireUser();
        return PageResponse.of(orderRepository.findByUser(user, pageable(page, size)).map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(String code) {
        return OrderResponse.from(requireOwnedOrder(code));
    }

    @Transactional(readOnly = true)
    public Order requireOwnedOrder(String code) {
        User user = currentUserService.requireUser();
        Order order = orderRepository.findByCode(code).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUser().getId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        return order;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> adminList(OrderStatus status, int page, int size) {
        var pageable = pageable(page, size);
        var result = status == null ? orderRepository.findAll(pageable) : orderRepository.findByStatus(status, pageable);
        return PageResponse.of(result.map(OrderResponse::from));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse adminUpdateStatus(String code, OrderStatus to) {
        Order order = orderRepository.findByCode(code).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getStatus().canTransitionTo(to)) throw new AppException(ErrorCode.INVALID_ORDER_STATUS);

        if (to == OrderStatus.CANCELLED) restock(order);
        if (to == OrderStatus.COMPLETED && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAt(Instant.now());
        }
        order.setStatus(to);
        return OrderResponse.from(orderRepository.save(order));
    }

    /**
     * Closes an unpaid MoMo order and returns its stock. Returns false when the order was no longer
     * PENDING_PAYMENT (already paid or cancelled), so it is safe to call repeatedly.
     */
    @Transactional
    public boolean cancelPendingPayment(String orderId, PaymentStatus finalPaymentStatus) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return false;
        List<OrderItem> lines = List.copyOf(order.getItems());
        if (orderRepository.cancelPending(orderId, finalPaymentStatus) == 0) return false;
        lines.forEach(i -> variantRepository.incrementStock(i.getVariantId(), i.getQuantity()));
        return true;
    }

    private void restock(Order order) {
        order.getItems().forEach(i -> variantRepository.incrementStock(i.getVariantId(), i.getQuantity()));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String newCode(Instant now) {
        StringBuilder sb = new StringBuilder("DH").append(CODE_DATE.format(now));
        for (int i = 0; i < 6; i++)
            sb.append(CODE_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }
}
```

- [ ] **Step 9: Implement the controllers**

`controller/OrderController.java`:
```java
package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderController {
    OrderService orderService;

    @PostMapping
    ApiResponse<OrderResponse> checkout(@RequestBody @Valid CheckoutRequest request) {
        return ApiResponse.ok(orderService.checkout(request));
    }

    @GetMapping
    ApiResponse<PageResponse<OrderResponse>> mine(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(orderService.myOrders(page, size));
    }

    @GetMapping("/{code}")
    ApiResponse<OrderResponse> get(@PathVariable String code) {
        return ApiResponse.ok(orderService.getMyOrder(code));
    }
}
```

`controller/AdminOrderController.java`:
```java
package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.service.OrderService;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminOrderController {
    OrderService orderService;

    record StatusRequest(@NotNull(message = "INVALID_INPUT") OrderStatus status) {}

    @GetMapping
    ApiResponse<PageResponse<OrderResponse>> list(@RequestParam(required = false) OrderStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orderService.adminList(status, page, size));
    }

    @PutMapping("/{code}/status")
    ApiResponse<OrderResponse> updateStatus(@PathVariable String code,
                                            @RequestBody @jakarta.validation.Valid StatusRequest request) {
        return ApiResponse.ok(orderService.adminUpdateStatus(code, request.status()));
    }
}
```

- [ ] **Step 10: Run to verify it passes**

Run: `mvn -q test -Dtest=OrderServiceTest`
Expected: 9 tests PASS.

- [ ] **Step 11: Write the concurrency and rollback test** `service/OrderConcurrencyTest.java` (deliberately NOT `@Transactional`, so each checkout really commits)

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {
    @Autowired TestDataFactory data;
    @Autowired CartService cartService;
    @Autowired OrderService orderService;
    @Autowired ProductVariantRepository variants;
    @Autowired OrderRepository orders;

    private CheckoutRequest request() {
        return new CheckoutRequest("Racer", "0901234567", "racer@example.com", "1 Race St", "Hà Nội", null,
                PaymentMethod.COD);
    }

    private void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private Callable<String> checkoutAs(String username, String variantId, CountDownLatch go) {
        return () -> {
            login(username);
            try {
                cartService.addItem(variantId, 1);
                go.await();
                orderService.checkout(request());
                return "OK";
            } catch (AppException e) {
                return e.getErrorCode().name();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    @Test
    void lastUnitIsSoldExactlyOnce() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Product p = data.product("race-" + tag, 100_000, true);
        ProductVariant v = data.variant(p, "M", "black", 1, null);
        User u1 = data.user("racer1-" + tag);
        User u2 = data.user("racer2-" + tag);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<String> f1 = pool.submit(checkoutAs(u1.getUsername(), v.getId(), go));
        Future<String> f2 = pool.submit(checkoutAs(u2.getUsername(), v.getId(), go));
        Thread.sleep(300);
        go.countDown();
        List<String> results = new ArrayList<>(List.of(f1.get(), f2.get()));
        pool.shutdown();

        assertThat(results).containsExactlyInAnyOrder("OK", "OUT_OF_STOCK");
        assertThat(variants.findById(v.getId()).orElseThrow().getStock()).isZero();
    }

    @Test
    void failureOnSecondLineRollsBackFirstLineReservation() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Product p = data.product("roll-" + tag, 100_000, true);
        ProductVariant first = data.variant(p, "M", "black", 5, null);
        ProductVariant second = data.variant(p, "L", "black", 5, null);
        User user = data.user("roller-" + tag);

        login(user.getUsername());
        try {
            cartService.addItem(first.getId(), 2);
            cartService.addItem(second.getId(), 5);
            second.setStock(1);            // stock shrinks after the item was added to the cart
            variants.save(second);

            assertThatThrownBy(() -> orderService.checkout(request())).isInstanceOf(AppException.class);

            assertThat(variants.findById(first.getId()).orElseThrow().getStock()).isEqualTo(5);
            assertThat(variants.findById(second.getId()).orElseThrow().getStock()).isEqualTo(1);
            assertThat(cartService.getCart().items()).hasSize(2);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
```

- [ ] **Step 12: Run the concurrency test**

Run: `mvn -q test -Dtest=OrderConcurrencyTest`
Expected: 2 tests PASS. If `lastUnitIsSoldExactlyOnce` fails with two `OK`, the stock decrement is not atomic: re-check `decrementStock` uses the conditional `where v.stock >= :qty`.

- [ ] **Step 13: Run the whole suite and commit**

Run: `mvn -q test`
Expected: all PASS.

```bash
git add src
git commit -m "feat: checkout with atomic stock reservation, orders and admin status flow"
```
