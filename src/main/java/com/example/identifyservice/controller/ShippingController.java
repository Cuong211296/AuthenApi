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
