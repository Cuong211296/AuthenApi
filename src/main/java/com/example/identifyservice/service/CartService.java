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
