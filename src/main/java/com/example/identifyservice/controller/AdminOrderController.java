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
