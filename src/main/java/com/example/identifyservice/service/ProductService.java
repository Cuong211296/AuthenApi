package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.CategoryResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.CategoryRepository;
import com.example.identifyservice.repository.ProductRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductService {
    ProductRepository productRepository;
    CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(String categorySlug, String q, int page, int size) {
        String cat = categorySlug == null ? "" : categorySlug.trim();
        String text = q == null ? "" : q.trim();
        if (text.length() > 100) text = text.substring(0, 100);
        // % and _ are LIKE wildcards; treat them literally by not matching them at all.
        if (text.contains("%") || text.contains("_")) text = "\u0000no-match\u0000";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(productRepository.search(cat, text, pageable).map(ProductSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getBySlug(String slug) {
        var product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductDetailResponse.from(product, true);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }
}
