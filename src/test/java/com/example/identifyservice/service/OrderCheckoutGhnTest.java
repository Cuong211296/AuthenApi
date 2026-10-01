package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghn.GhnDistrict;
import com.example.identifyservice.ghn.GhnProvince;
import com.example.identifyservice.ghn.GhnWard;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCheckoutGhnTest {
    @Autowired OrderService orders;
    @Autowired CartService cart;
    @Autowired TestDataFactory data;
    @Autowired ProductVariantRepository variants;
    @Autowired OrderRepository orderRepository;
    @Autowired EntityManager em;
    @Autowired FakeGhnGateway ghn;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingQuoteService quoteService;

    ProductVariant m;

    @BeforeEach
    void setUp() {
        resetFakes();
        ghn.useSampleData();
        data.user("alice");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", "x", "ROLE_USER"));
        Product tee = data.product("ghn-co-tee", 200_000, true);
        m = data.variant(tee, "M", "white", 5, null);
    }

    @AfterEach
    void tearDown() {
        resetFakes();
        SecurityContextHolder.clearContext();
    }

    private void resetFakes() {
        ghn.reset();
        ghtk.reset();
        quoteService.clearCache();
    }

    private CheckoutRequest ids(Integer provinceId, Integer districtId, String wardCode, String province,
                                String district, String ward) {
        return new CheckoutRequest("Alice", "0901234567", "alice@example.com", "12 Nguyen Hue", province, ward, null,
                PaymentMethod.COD, provinceId, districtId, wardCode, district);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((AppException) t).getErrorCode();
    }

    @Test
    void idsPathStoresResolvedNamesDistrictSourceAndFeeAndIgnoresClientNames() {
        ghn.returnFee(37_000);
        cart.addItem(m.getId(), 2);

        OrderResponse order = orders.checkout(ids(202, 1442, "20308", "Atlantis", "Fake District", "Fake Ward"));

        assertThat(order.province()).isEqualTo("Hồ Chí Minh");
        assertThat(order.district()).isEqualTo("Quận 1");
        assertThat(order.ward()).isEqualTo("Phường Bến Nghé");
        assertThat(order.shippingSource()).isEqualTo(ShippingSource.GHN);
        assertThat(order.shippingFee()).isEqualTo(37_000);
        assertThat(order.total()).isEqualTo(437_000);
        assertThat(order.weightGrams()).isEqualTo(600);
        assertThat(ghn.feeCalls).hasSize(1);
        assertThat(ghn.feeCalls.get(0).insuranceValue()).isEqualTo(400_000);

        em.flush();
        em.clear();
        Order stored = orderRepository.findByCode(order.code()).orElseThrow();
        assertThat(stored.getDistrict()).isEqualTo("Quận 1");
        assertThat(stored.getProvince()).isEqualTo("Hồ Chí Minh");
        assertThat(orders.getMyOrder(order.code()).district()).isEqualTo("Quận 1");
    }

    @Test
    void textPathIsUnchangedAndStoresTheOptionalDistrictOnlyWhenGiven() {
        ghtk.returnFee(31_000);
        cart.addItem(m.getId(), 1);
        OrderResponse plain = orders.checkout(ids(null, null, null, "Hà Nội", null, "Phường 1"));
        assertThat(plain.district()).isNull();
        assertThat(plain.province()).isEqualTo("Hà Nội");
        assertThat(plain.shippingSource()).isEqualTo(ShippingSource.GHTK);
        assertThat(ghn.feeCalls).isEmpty();

        cart.addItem(m.getId(), 1);
        OrderResponse withDistrict = orders.checkout(ids(null, null, null, "Hà Nội", " Ba Đình ", "Phường 1"));
        assertThat(withDistrict.district()).isEqualTo("Ba Đình");
    }

    @Test
    void mismatchedOrUnknownIdsAreInvalidInputAndLeaveStockAndCartUntouched() {
        cart.addItem(m.getId(), 2);
        for (CheckoutRequest bad : List.of(ids(202, 1490, "1A0101", "Hà Nội", null, "x"),   // district of another province
                ids(202, 1442, "1A0101", null, null, null),                                  // ward of another district
                ids(999, 1442, "20308", null, null, null),
                ids(202, 1442, "NOPE", null, null, null))) {
            assertThatThrownBy(() -> orders.checkout(bad)).isInstanceOf(AppException.class)
                    .extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT);
        }
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(orders.myOrders(0, 10).items()).isEmpty();
        assertThat(cart.getCart().items()).hasSize(1);
        assertThat(ghn.feeCalls).isEmpty();
    }

    @Test
    void addressWithoutIdsOrTextNamesIsInvalidInput() {
        cart.addItem(m.getId(), 1);
        for (CheckoutRequest bad : List.of(ids(null, null, null, null, null, null),
                ids(null, null, null, "Hà Nội", null, " "), ids(null, null, null, " ", null, "Phường 1"),
                ids(202, 1442, null, null, null, null), ids(null, 1442, "20308", null, null, null)))
            assertThatThrownBy(() -> orders.checkout(bad)).isInstanceOf(AppException.class)
                    .extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void ghnFeeDownFallsBackToGhtkThenTableAndStillStoresResolvedNames() {
        cart.addItem(m.getId(), 1);
        ghtk.returnFee(31_000);
        OrderResponse viaGhtk = orders.checkout(ids(202, 1442, "20308", null, null, null));
        assertThat(viaGhtk.shippingSource()).isEqualTo(ShippingSource.GHTK);
        assertThat(viaGhtk.shippingFee()).isEqualTo(31_000);
        assertThat(viaGhtk.district()).isEqualTo("Quận 1");
        assertThat(viaGhtk.ward()).isEqualTo("Phường Bến Nghé");

        resetFakes();
        ghn.useSampleData();
        cart.addItem(m.getId(), 1);
        OrderResponse viaTable = orders.checkout(ids(202, 1442, "20308", null, null, null));
        assertThat(viaTable.shippingSource()).isEqualTo(ShippingSource.TABLE);
        assertThat(viaTable.shippingFee()).isEqualTo(25_000);           // "Hồ Chí Minh" matches "TP Hồ Chí Minh"
        assertThat(viaTable.province()).isEqualTo("Hồ Chí Minh");        // the GHN master data name is what is stored
    }

    @Test
    void masterDataDownWithTextNamesCheckoutUsesTheTextPath() {
        ghn.masterDataDown();
        ghtk.returnFee(31_000);
        cart.addItem(m.getId(), 1);
        OrderResponse order = orders.checkout(ids(202, 1442, "20308", "Hà Nội", null, "Phường 1"));
        assertThat(order.province()).isEqualTo("Hà Nội");
        assertThat(order.ward()).isEqualTo("Phường 1");
        assertThat(order.shippingSource()).isEqualTo(ShippingSource.GHTK);
    }

    @Test
    void masterDataDownWithIdsOnlyIsProviderUnavailableAndTouchesNothing() {
        ghn.masterDataDown();
        cart.addItem(m.getId(), 1);
        assertThatThrownBy(() -> orders.checkout(ids(202, 1442, "20308", null, null, null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e))
                .isEqualTo(ErrorCode.SHIPPING_PROVIDER_UNAVAILABLE);
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void provinceNoCarrierAndNoTableCanServeIsShippingNotAvailable() {
        ghn.provinceHandler = () -> List.of(new GhnProvince(900, "Atlantis"));
        ghn.districtHandler = id -> List.of(new GhnDistrict(9001, 900, "D"));
        ghn.wardHandler = id -> List.of(new GhnWard("W1", 9001, "W"));
        cart.addItem(m.getId(), 2);
        assertThatThrownBy(() -> orders.checkout(ids(900, 9001, "W1", null, null, null)))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.SHIPPING_NOT_AVAILABLE);
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(cart.getCart().items()).hasSize(1);
    }
}
