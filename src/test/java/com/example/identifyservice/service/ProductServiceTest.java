package com.example.identifyservice.service;

import com.example.identifyservice.dto.response.ProductDetailResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.dto.response.VariantResponse;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductServiceTest {
    @Autowired ProductService productService;
    @Autowired TestDataFactory data;
    @Autowired ProductVariantRepository variantRepository;

    @Test
    void listsOnlyActiveProducts() {
        data.product("tee-active", 199_000, true);
        data.product("tee-hidden", 199_000, false);
        var page = productService.search("", "", 0, 50);
        assertThat(page.items()).extracting(ProductSummaryResponse::slug)
                .contains("tee-active").doesNotContain("tee-hidden");
    }

    @Test
    void filtersByCategoryAndSearchText() {
        data.product("linen-shirt", 350_000, true, "tops");
        data.product("slim-jeans", 450_000, true, "bottoms");

        assertThat(productService.search("bottoms", "", 0, 50).items())
                .extracting(ProductSummaryResponse::slug).contains("slim-jeans").doesNotContain("linen-shirt");
        assertThat(productService.search("", "LINEN", 0, 50).items())
                .extracting(ProductSummaryResponse::slug).contains("linen-shirt").doesNotContain("slim-jeans");
    }

    @Test
    void oddSearchTextDoesNotFailOrMatchEverything() {
        data.product("plain-tee", 100_000, true);
        assertThat(productService.search("", "   ", 0, 50).items()).isNotNull();
        assertThat(productService.search("", "%", 0, 50).items())
                .extracting(ProductSummaryResponse::slug).doesNotContain("plain-tee");
        assertThat(productService.search("", "' or 1=1 --", 0, 50).items()).isEmpty();
    }

    @Test
    void detailUsesEffectivePriceAndHidesInactiveVariants() {
        Product p = data.product("hoodie", 300_000, true);
        data.variant(p, "M", "black", 5, null);
        data.variant(p, "L", "black", 3, 320_000L);
        ProductVariant hidden = data.variant(p, "XL", "black", 9, null);
        hidden.setActive(false);
        variantRepository.save(hidden);

        ProductDetailResponse detail = productService.getBySlug("hoodie");
        assertThat(detail.variants()).extracting(VariantResponse::size).containsExactlyInAnyOrder("M", "L");
        assertThat(detail.variants()).filteredOn(v -> v.size().equals("M")).extracting(VariantResponse::price)
                .containsExactly(300_000L);
        assertThat(detail.variants()).filteredOn(v -> v.size().equals("L")).extracting(VariantResponse::price)
                .containsExactly(320_000L);
    }

    @Test
    void unknownOrInactiveSlugIsNotFound() {
        data.product("retired", 100_000, false);
        for (String slug : new String[]{"retired", "does-not-exist"}) {
            assertThatThrownBy(() -> productService.getBySlug(slug))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }
}
