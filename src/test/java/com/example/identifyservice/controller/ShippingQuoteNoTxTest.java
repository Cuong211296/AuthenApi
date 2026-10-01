package com.example.identifyservice.controller;

import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.ghtk.GhtkFeeResult;
import com.example.identifyservice.repository.CartRepository;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.repository.UserRepository;
import com.example.identifyservice.service.ShippingQuoteService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately NOT @Transactional: data is committed so the real endpoint runs its own transactions, which is what
 * exposed a rollback-only leak when an AppException crossed a transactional proxy inside an outer transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippingQuoteNoTxTest {
    @Autowired MockMvc mvc;
    @Autowired TestDataFactory data;
    @Autowired CartRepository carts;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired UserRepository users;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingQuoteService quoteService;

    Cart cart;
    Product product;

    @BeforeEach
    void setUp() {
        ghtk.reset();
        quoteService.clearCache();
        product = data.product("notx-tee", 200_000, true);
        ProductVariant v = data.variant(product, "M", "white", 9, null);
        cart = Cart.builder().user(data.user("notx-quoter")).build();
        cart.getItems().add(CartItem.builder().cart(cart).variant(v).quantity(1).build());
        cart = carts.save(cart);
    }

    @AfterEach
    void cleanUp() {
        ghtk.reset();
        quoteService.clearCache();
        carts.delete(cart);
        variants.deleteAll(variants.findAll().stream().filter(v -> v.getProduct().getId().equals(product.getId())).toList());
        products.deleteById(product.getId());
        users.findByUsername("notx-quoter").ifPresent(users::delete);
    }

    private ResultActions quote(String province) throws Exception {
        return mvc.perform(post("/shipping/quote")
                .with(jwt().jwt(j -> j.subject("notx-quoter")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"province\":\"" + province + "\",\"ward\":\"Phường 1\",\"address\":\"1 St\"}"));
    }

    @Test
    void ghtkRefusesAndProvinceNotInTableAnswers200NotDeliverable() throws Exception {
        ghtk.returnResult(new GhtkFeeResult(true, false, 0, null));
        quote("Atlantis").andExpect(status().isOk())
                .andExpect(jsonPath("$.result.deliverable").value(false))
                .andExpect(jsonPath("$.result.fee").value(0));
    }

    @Test
    void ghtkDownAndProvinceNotInTableAnswers400InvalidProvince() throws Exception {
        quote("Atlantis").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2009));
    }

    @Test
    void ghtkRefusesAndProvinceInTableFallsBackTo200() throws Exception {
        ghtk.returnResult(new GhtkFeeResult(true, false, 0, null));
        quote("Hà Nội").andExpect(status().isOk())
                .andExpect(jsonPath("$.result.source").value("TABLE"))
                .andExpect(jsonPath("$.result.deliverable").value(true));
    }
}
