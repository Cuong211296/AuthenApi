package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CategoryRequest;
import com.example.identifyservice.dto.request.ProductRequest;
import com.example.identifyservice.dto.request.VariantRequest;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminCatalogServiceTest {
    @Autowired AdminCatalogService admin;
    @Autowired ProductService publicService;

    private ProductRequest product(String slug, String categoryId) {
        return new ProductRequest("Name " + slug, slug, "desc", categoryId, 250_000, null, null, "https://img/x.jpg", true);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((AppException) t).getErrorCode();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanBuildCatalogAndItAppearsPublicly() {
        var cat = admin.createCategory(new CategoryRequest("Shirts", "shirts"));
        var created = admin.createProduct(product("oxford-shirt", cat.id()));
        var variant = admin.createVariant(created.id(), new VariantRequest("M", "blue", "OXF-M-BLUE", 10, null, null));

        assertThat(variant.price()).isEqualTo(250_000);
        assertThat(publicService.getBySlug("oxford-shirt").variants()).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateSlugAndSkuAreRejected() {
        var created = admin.createProduct(product("dup-tee", null));
        admin.createVariant(created.id(), new VariantRequest("M", "red", "DUP-1", 1, null, null));

        assertThatThrownBy(() -> admin.createProduct(product("dup-tee", null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SLUG_EXISTED);
        assertThatThrownBy(() -> admin.createVariant(created.id(), new VariantRequest("L", "red", "DUP-1", 1, null, null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SKU_EXISTED);

        var other = admin.createProduct(product("other-tee", null));
        assertThatThrownBy(() -> admin.updateProduct(other.id(), product("dup-tee", null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SLUG_EXISTED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivatingHidesFromPublicButAdminStillSees() {
        var created = admin.createProduct(product("seasonal", null));
        admin.deactivateProduct(created.id());

        assertThat(publicService.search("", "seasonal", 0, 20).items()).isEmpty();
        assertThat(admin.listProducts(0, 50).items()).extracting(ProductSummaryResponse::slug).contains("seasonal");
        assertThat(admin.getProduct(created.id()).active()).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void variantCanBeDeactivatedAndUpdated() {
        var created = admin.createProduct(product("polo", null));
        var v = admin.createVariant(created.id(), new VariantRequest("M", "green", "POLO-M-G", 4, null, null));
        var updated = admin.updateVariant(v.id(), new VariantRequest("M", "green", "POLO-M-G", 9, 199_000L, null));
        assertThat(updated.stock()).isEqualTo(9);
        assertThat(updated.price()).isEqualTo(199_000);

        admin.deactivateVariant(v.id());
        assertThat(publicService.getBySlug("polo").variants()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void skuIsTrimmedBeforeUniquenessCheck() {
        var created = admin.createProduct(product("trim-tee", null));
        var first = admin.createVariant(created.id(), new VariantRequest("M", "red", "TRIM-1", 1, null, null));
        var second = admin.createVariant(created.id(), new VariantRequest("L", "red", "TRIM-2", 1, null, null));

        assertThatThrownBy(() -> admin.createVariant(created.id(), new VariantRequest("S", "red", " TRIM-1", 1, null, null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SKU_EXISTED);

        var updated = admin.updateVariant(first.id(), new VariantRequest("M", "red", "TRIM-1 ", 5, null, null));
        assertThat(updated.stock()).isEqualTo(5);

        assertThatThrownBy(() -> admin.updateVariant(second.id(), new VariantRequest("L", "red", " TRIM-1 ", 1, null, null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SKU_EXISTED);
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminIsDenied() {
        assertThatThrownBy(() -> admin.createCategory(new CategoryRequest("X", "x")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateSizeColourCombinationIsRejected() {
        var created = admin.createProduct(product("combo-tee", null));
        admin.createVariant(created.id(), new VariantRequest("M", "red", "COMBO-1", 1, null, null));
        var other = admin.createVariant(created.id(), new VariantRequest("L", "red", "COMBO-2", 1, null, null));

        assertThatThrownBy(() -> admin.createVariant(created.id(), new VariantRequest("M", "red", "COMBO-3", 1, null, null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.VARIANT_EXISTED);
        assertThatThrownBy(() -> admin.updateVariant(other.id(), new VariantRequest("M", "red", "COMBO-2", 1, null, null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.VARIANT_EXISTED);
        // updating a variant with its own combination is fine
        assertThat(admin.updateVariant(other.id(), new VariantRequest("L", "red", "COMBO-2", 7, null, null)).stock()).isEqualTo(7);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nullActiveOnUpdateKeepsCurrentValue() {
        var created = admin.createProduct(product("keep-tee", null));
        var v = admin.createVariant(created.id(), new VariantRequest("M", "red", "KEEP-1", 1, null, null));
        assertThat(v.active()).isTrue();
        admin.deactivateVariant(v.id());
        admin.deactivateProduct(created.id());

        var p = admin.updateProduct(created.id(),
                new ProductRequest("Renamed", "keep-tee", "desc", null, 250_000, null, null, null, null));
        assertThat(p.active()).isFalse();
        var uv = admin.updateVariant(v.id(), new VariantRequest("M", "red", "KEEP-1", 2, null, null));
        assertThat(uv.active()).isFalse();

        var reactivated = admin.updateProduct(created.id(),
                new ProductRequest("Renamed", "keep-tee", "desc", null, 250_000, null, null, null, true));
        assertThat(reactivated.active()).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void costPriceIsStoredUpdatedAndNullable() {
        var created = admin.createProduct(new ProductRequest("Cost tee", "cost-tee", "d", null, 250_000, 90_000L, null, null, true));
        assertThat(created.costPrice()).isEqualTo(90_000L);

        var updated = admin.updateProduct(created.id(),
                new ProductRequest("Cost tee", "cost-tee", "d", null, 250_000, 95_000L, null, null, true));
        assertThat(updated.costPrice()).isEqualTo(95_000L);

        var cleared = admin.updateProduct(created.id(),
                new ProductRequest("Cost tee", "cost-tee", "d", null, 250_000, null, null, null, true));
        assertThat(cleared.costPrice()).isNull();

        var none = admin.createProduct(new ProductRequest("No cost", "no-cost", "d", null, 250_000, null, null, null, true));
        assertThat(none.costPrice()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void weightIsStoredUpdatedAndNullable() {
        var created = admin.createProduct(new ProductRequest("Weight tee", "weight-tee", "d", null, 250_000, null, 450, null, true));
        assertThat(created.weightGrams()).isEqualTo(450);

        var updated = admin.updateProduct(created.id(),
                new ProductRequest("Weight tee", "weight-tee", "d", null, 250_000, null, 600, null, true));
        assertThat(updated.weightGrams()).isEqualTo(600);

        var cleared = admin.updateProduct(created.id(),
                new ProductRequest("Weight tee", "weight-tee", "d", null, 250_000, null, null, null, true));
        assertThat(cleared.weightGrams()).isNull();

        var none = admin.createProduct(new ProductRequest("No weight", "no-weight", "d", null, 250_000, null, null, null, true));
        assertThat(none.weightGrams()).isNull();
    }
}
