package com.example.identifyservice.service;

import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Measures the current user's cart in its own short read-only transaction, so the shipping quote can then call
 * GHTK outside of any transaction (no DB connection is held during the HTTP call).
 */
@Service
@RequiredArgsConstructor
public class CartMeasurer {
    private final CartRepository cartRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public CartMeasure measureCurrentCart() {
        Cart cart = cartRepository.findByUser(currentUserService.requireUser())
                .orElseThrow(() -> new AppException(ErrorCode.CART_EMPTY));
        if (cart.getItems().isEmpty()) throw new AppException(ErrorCode.CART_EMPTY);
        return CartMeasure.of(cart);
    }
}
