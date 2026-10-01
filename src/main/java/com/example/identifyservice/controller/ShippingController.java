package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.ShippingFeeUpdateRequest;
import com.example.identifyservice.dto.request.ShippingQuoteRequest;
import com.example.identifyservice.dto.request.ShippingRateRequest;
import com.example.identifyservice.dto.response.ShippingFeeResponse;
import com.example.identifyservice.dto.response.ShippingQuoteResponse;
import com.example.identifyservice.dto.response.ShippingRateResponse;
import com.example.identifyservice.service.ShippingQuoteService;
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
    ShippingQuoteService shippingQuoteService;

    /** Authenticated: quotes the shipping fee of the signed-in user's current cart. */
    @PostMapping("/shipping/quote")
    ApiResponse<ShippingQuoteResponse> quote(@RequestBody @Valid ShippingQuoteRequest request) {
        return ApiResponse.ok(ShippingQuoteResponse.from(
                shippingQuoteService.quoteCurrentCart(request.province(), request.ward(), request.address())));
    }

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
    ApiResponse<ShippingRateResponse> adminUpdate(@PathVariable String id, @RequestBody @Valid ShippingFeeUpdateRequest request) {
        return ApiResponse.ok(shippingService.updateFee(id, request.fee()));
    }
}
