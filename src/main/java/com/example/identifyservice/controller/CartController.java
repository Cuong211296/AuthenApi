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
    ApiResponse<CartResponse> update(@PathVariable String variantId, @RequestBody @Valid CartItemRequest request) {
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
