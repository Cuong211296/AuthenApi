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
