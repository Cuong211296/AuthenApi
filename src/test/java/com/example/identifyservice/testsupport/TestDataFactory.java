package com.example.identifyservice.testsupport;

import com.example.identifyservice.entity.Category;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.OrderItem;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.repository.CategoryRepository;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.PaymentRepository;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class TestDataFactory {
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;

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

    /** A MoMo order in PENDING_PAYMENT holding qty of the variant (stock is NOT decremented here). */
    public Order pendingMomoOrder(User user, ProductVariant variant, int qty, Instant expiresAt) {
        long unit = variant.effectivePrice();
        Order order = Order.builder().code("DHTEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .user(user).status(OrderStatus.PENDING_PAYMENT).paymentMethod(PaymentMethod.MOMO)
                .paymentStatus(PaymentStatus.UNPAID).receiverName("Test").phone("0901234567")
                .email("test@example.com").address("1 Test St").province("Hà Nội")
                .subtotal(unit * qty).shippingFee(25_000).total(unit * qty + 25_000).expiresAt(expiresAt).build();
        order.getItems().add(OrderItem.builder().order(order).variantId(variant.getId())
                .productName(variant.getProduct().getName()).size(variant.getSize()).color(variant.getColor())
                .unitPrice(unit).quantity(qty).build());
        return orders.save(order);
    }

    public Payment attempt(Order order, String providerOrderId) {
        return payments.save(Payment.builder().order(order).requestId(UUID.randomUUID().toString())
                .providerOrderId(providerOrderId).amount(order.getTotal())
                .status(PaymentAttemptStatus.PENDING).build());
    }
}
