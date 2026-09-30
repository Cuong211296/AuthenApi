package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.response.CategoryResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductController {
    ProductService productService;

    @GetMapping("/products")
    ApiResponse<PageResponse<ProductSummaryResponse>> list(
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(productService.search(category, q, page, size));
    }

    @GetMapping("/products/{slug}")
    ApiResponse<ProductDetailResponse> detail(@PathVariable String slug) {
        return ApiResponse.ok(productService.getBySlug(slug));
    }

    @GetMapping("/categories")
    ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.ok(productService.categories());
    }
}
