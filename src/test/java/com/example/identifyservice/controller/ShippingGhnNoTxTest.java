package com.example.identifyservice.controller;

import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.repository.CartRepository;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.repository.UserRepository;
import com.example.identifyservice.service.ShippingQuoteService;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately NOT @Transactional (see ShippingQuoteNoTxTest): the real endpoints run their own transactions.
 * Covers the public GHN master-data proxy, GET /shipping/config and POST /shipping/quote in GHN id mode.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippingGhnNoTxTest {
    @Autowired MockMvc mvc;
    @Autowired TestDataFactory data;
    @Autowired CartRepository carts;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired UserRepository users;
    @Autowired FakeGhnGateway ghn;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingQuoteService quoteService;

    Cart cart;
    Product product;

    @BeforeEach
    void setUp() {
        reset();
        product = data.product("ghn-notx-tee", 200_000, true);
        ProductVariant v = data.variant(product, "M", "white", 9, null);
        cart = Cart.builder().user(data.user("ghn-notx-quoter")).build();
        cart.getItems().add(CartItem.builder().cart(cart).variant(v).quantity(1).build());
        cart = carts.save(cart);
    }

    @AfterEach
    void cleanUp() {
        reset();
        carts.delete(cart);
        variants.deleteAll(variants.findAll().stream().filter(v -> v.getProduct().getId().equals(product.getId())).toList());
        products.deleteById(product.getId());
        users.findByUsername("ghn-notx-quoter").ifPresent(users::delete);
    }

    private void reset() {
        ghn.reset();
        ghtk.reset();
        quoteService.clearCache();
    }

    private ResultActions quote(String body) throws Exception {
        return mvc.perform(post("/shipping/quote")
                .with(jwt().jwt(j -> j.subject("ghn-notx-quoter")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void configReportsGhnIdsWhenMasterDataIsReachableAndTextOtherwise() throws Exception {
        mvc.perform(get("/shipping/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.provider").value("GHTK"))      // test profile: GHTK on, GHN master data down
                .andExpect(jsonPath("$.result.addressMode").value("TEXT"));

        quoteService.clearCache();                                         // forget the remembered outage
        ghn.useSampleData();
        mvc.perform(get("/shipping/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.provider").value("GHN"))
                .andExpect(jsonPath("$.result.addressMode").value("GHN_IDS"));
    }

    @Test
    void masterDataEndpointsArePublicAndReturnIdNameAndCodeNameLists() throws Exception {
        ghn.useSampleData();
        mvc.perform(get("/shipping/ghn/provinces")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].id").value(201))
                .andExpect(jsonPath("$.result[0].name").value("Hà Nội"));
        mvc.perform(get("/shipping/ghn/districts").param("provinceId", "202")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].id").value(1442))
                .andExpect(jsonPath("$.result[0].name").value("Quận 1"));
        mvc.perform(get("/shipping/ghn/wards").param("provinceId", "202").param("districtId", "1442")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].code").value("20308"))
                .andExpect(jsonPath("$.result[0].name").value("Phường Bến Nghé"));
        mvc.perform(get("/shipping/ghn/districts").param("provinceId", "999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(0));
    }

    @Test
    void masterDataEndpointsAnswer502WithTheDedicatedCodeWhenGhnIsDown() throws Exception {
        mvc.perform(get("/shipping/ghn/provinces")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(2019));
        mvc.perform(get("/shipping/ghn/districts").param("provinceId", "202")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(2019));
        mvc.perform(get("/shipping/ghn/wards").param("provinceId", "202").param("districtId", "1442")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(2019));
    }

    @Test
    void quoteWithGhnIdsUsesTheGhnFee() throws Exception {
        ghn.useSampleData();
        ghn.returnFee(37_000);
        quote("{\"provinceId\":202,\"districtId\":1442,\"wardCode\":\"20308\",\"address\":\"1 St\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.fee").value(37000))
                .andExpect(jsonPath("$.result.source").value("GHN"))
                .andExpect(jsonPath("$.result.estimated").value(false));
    }

    @Test
    void quoteFallsBackThroughGhtkToTheTableWhenGhnFeeIsDown() throws Exception {
        ghn.useSampleData();                                       // master data ok, fee down
        ghtk.returnFee(31_000);
        quote("{\"provinceId\":202,\"districtId\":1442,\"wardCode\":\"20308\",\"address\":\"1 St\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.source").value("GHTK"));

        reset();
        ghn.useSampleData();
        quote("{\"provinceId\":202,\"districtId\":1442,\"wardCode\":\"20308\",\"address\":\"1 St\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.source").value("TABLE"))
                .andExpect(jsonPath("$.result.estimated").value(true));
    }

    @Test
    void crossFieldValidationRequiresIdsOrTextNames() throws Exception {
        ghn.useSampleData();
        quote("{\"address\":\"1 St\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1011));
        quote("{\"province\":\"Hà Nội\",\"address\":\"1 St\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        quote("{\"districtId\":1442,\"wardCode\":\"20308\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        quote("{\"provinceId\":202,\"districtId\":1442,\"wardCode\":\"1A0101\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        quote("{\"provinceId\":-1,\"districtId\":1442,\"wardCode\":\"20308\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
    }

    @Test
    void wardsRequireAKnownProvinceAndDistrictAndNeverCallGhnForUnknownIds() throws Exception {
        ghn.useSampleData();
        for (String[] bad : new String[][]{{"999", "1442"}, {"202", "1490"}, {"202", "-1"}, {"-3", "1442"}, {"202", "987654"}})
            mvc.perform(get("/shipping/ghn/wards").param("provinceId", bad[0]).param("districtId", bad[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1011));
        org.assertj.core.api.Assertions.assertThat(ghn.wardCalls).hasValue(0);
        mvc.perform(get("/shipping/ghn/wards").param("districtId", "1442")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
    }

    @Test
    void malformedOrMissingQueryParametersAre400InvalidInput() throws Exception {
        ghn.useSampleData();
        mvc.perform(get("/shipping/ghn/districts").param("provinceId", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        mvc.perform(get("/shipping/ghn/districts")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
    }

    @Test
    void textModeStillWorksWithoutAnyIds() throws Exception {
        quote("{\"province\":\"Hà Nội\",\"ward\":\"Phường Phúc Xá\",\"address\":\"1 St\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.source").value("TABLE"));
    }

    @Test
    void unauthenticatedQuoteIsRejectedWhileConfigIsPublic() throws Exception {
        mvc.perform(post("/shipping/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provinceId\":202,\"districtId\":1442,\"wardCode\":\"20308\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/shipping/config")).andExpect(status().isOk());
    }
}
