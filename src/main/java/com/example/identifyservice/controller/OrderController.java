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
