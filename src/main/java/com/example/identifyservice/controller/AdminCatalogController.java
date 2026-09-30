package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.CategoryRequest;
import com.example.identifyservice.dto.request.ProductRequest;
import com.example.identifyservice.dto.request.VariantRequest;
import com.example.identifyservice.dto.response.CategoryResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.dto.response.VariantResponse;
import com.example.identifyservice.service.AdminCatalogService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminCatalogController {
    AdminCatalogService service;

    @PostMapping("/categories")
    ApiResponse<CategoryResponse> createCategory(@RequestBody @Valid CategoryRequest request) {
        return ApiResponse.ok(service.createCategory(request));
    }

    @GetMapping("/products")
    ApiResponse<PageResponse<ProductSummaryResponse>> listProducts(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listProducts(page, size));
    }

    @GetMapping("/products/{id}")
    ApiResponse<ProductDetailResponse> getProduct(@PathVariable String id) {
        return ApiResponse.ok(service.getProduct(id));
    }

    @PostMapping("/products")
    ApiResponse<ProductDetailResponse> createProduct(@RequestBody @Valid ProductRequest request) {
        return ApiResponse.ok(service.createProduct(request));
    }

    @PutMapping("/products/{id}")
    ApiResponse<ProductDetailResponse> updateProduct(@PathVariable String id, @RequestBody @Valid ProductRequest request) {
        return ApiResponse.ok(service.updateProduct(id, request));
    }

    @DeleteMapping("/products/{id}")
    ApiResponse<Void> deactivateProduct(@PathVariable String id) {
        service.deactivateProduct(id);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/products/{productId}/variants")
    ApiResponse<VariantResponse> createVariant(@PathVariable String productId, @RequestBody @Valid VariantRequest request) {
        return ApiResponse.ok(service.createVariant(productId, request));
    }

    @PutMapping("/variants/{id}")
    ApiResponse<VariantResponse> updateVariant(@PathVariable String id, @RequestBody @Valid VariantRequest request) {
        return ApiResponse.ok(service.updateVariant(id, request));
    }

    @DeleteMapping("/variants/{id}")
    ApiResponse<Void> deactivateVariant(@PathVariable String id) {
        service.deactivateVariant(id);
        return ApiResponse.<Void>builder().build();
    }
}
