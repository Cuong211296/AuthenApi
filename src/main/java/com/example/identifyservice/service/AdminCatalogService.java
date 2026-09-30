package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CategoryRequest;
import com.example.identifyservice.dto.request.ProductRequest;
import com.example.identifyservice.dto.request.VariantRequest;
import com.example.identifyservice.dto.response.CategoryResponse;
import com.example.identifyservice.dto.response.PageResponse;
import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.dto.response.VariantResponse;
import com.example.identifyservice.entity.Category;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.CategoryRepository;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogService {
    CategoryRepository categoryRepository;
    ProductRepository productRepository;
    ProductVariantRepository variantRepository;

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) throw new AppException(ErrorCode.SLUG_EXISTED);
        return CategoryResponse.from(categoryRepository.save(
                Category.builder().name(request.name().trim()).slug(request.slug()).build()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> listProducts(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(productRepository.findAll(pageable).map(ProductSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(String id) {
        return ProductDetailResponse.from(requireProduct(id), false);
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySlug(request.slug())) throw new AppException(ErrorCode.SLUG_EXISTED);
        Product product = Product.builder().build();
        apply(product, request);
        return ProductDetailResponse.from(productRepository.save(product), false);
    }

    @Transactional
    public ProductDetailResponse updateProduct(String id, ProductRequest request) {
        Product product = requireProduct(id);
        if (!product.getSlug().equals(request.slug()) && productRepository.existsBySlug(request.slug()))
            throw new AppException(ErrorCode.SLUG_EXISTED);
        apply(product, request);
        return ProductDetailResponse.from(productRepository.save(product), false);
    }

    @Transactional
    public void deactivateProduct(String id) {
        Product product = requireProduct(id);
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional
    public VariantResponse createVariant(String productId, VariantRequest request) {
        Product product = requireProduct(productId);
        if (variantRepository.existsBySku(request.sku())) throw new AppException(ErrorCode.SKU_EXISTED);
        ProductVariant variant = ProductVariant.builder().product(product).build();
        apply(variant, request);
        product.getVariants().add(variant);
        return VariantResponse.from(variantRepository.save(variant));
    }

    @Transactional
    public VariantResponse updateVariant(String variantId, VariantRequest request) {
        ProductVariant variant = requireVariant(variantId);
        if (!variant.getSku().equals(request.sku()) && variantRepository.existsBySku(request.sku()))
            throw new AppException(ErrorCode.SKU_EXISTED);
        apply(variant, request);
        return VariantResponse.from(variantRepository.save(variant));
    }

    @Transactional
    public void deactivateVariant(String variantId) {
        ProductVariant variant = requireVariant(variantId);
        variant.setActive(false);
        variantRepository.save(variant);
    }

    private void apply(Product product, ProductRequest r) {
        product.setName(r.name().trim());
        product.setSlug(r.slug());
        product.setDescription(r.description());
        product.setBasePrice(r.basePrice());
        product.setImageUrl(r.imageUrl());
        product.setActive(r.active() == null || r.active());
        product.setCategory(r.categoryId() == null || r.categoryId().isBlank() ? null
                : categoryRepository.findById(r.categoryId())
                        .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND)));
    }

    private void apply(ProductVariant v, VariantRequest r) {
        v.setSize(r.size().trim());
        v.setColor(r.color().trim());
        v.setSku(r.sku().trim());
        v.setStock(r.stock());
        v.setPrice(r.price());
        v.setActive(r.active() == null || r.active());
    }

    private Product requireProduct(String id) {
        return productRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductVariant requireVariant(String id) {
        return variantRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
    }
}
