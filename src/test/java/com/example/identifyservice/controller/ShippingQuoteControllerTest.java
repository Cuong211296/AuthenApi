package com.example.identifyservice.controller;

import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.repository.CartRepository;
import com.example.identifyservice.service.ShippingQuoteService;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShippingQuoteControllerTest {
    static final String BODY = "{\"province\":\"Hà Nội\",\"ward\":\"Phường Bến Nghé\",\"address\":\"12 Nguyen Hue\"}";

    @Autowired MockMvc mvc;
    @Autowired TestDataFactory data;
    @Autowired CartRepository carts;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingQuoteService quoteService;

    @org.junit.jupiter.api.AfterEach
    void resetGhtk() {
        ghtk.reset();
        quoteService.clearCache();
    }

    @BeforeEach
    void setUp() {
        ghtk.reset();
        quoteService.clearCache();
        data.user("quoter");
    }

    private static RequestPostProcessor quoter() {
        return jwt().jwt(j -> j.subject("quoter")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private ResultActions call(String body) throws Exception {
        return mvc.perform(post("/shipping/quote").with(quoter()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void cartWith(String slug, int qty) {
        ProductVariant v = data.variant(data.product(slug, 200_000, true), "M", "white", 9, null);
        Cart cart = Cart.builder().user(data.user("quoter")).build();
        cart.getItems().add(CartItem.builder().cart(cart).variant(v).quantity(qty).build());
        carts.save(cart);
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(post("/shipping/quote").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quotesTheUsersCurrentCart() throws Exception {
        cartWith("endpoint-tee", 3);
        ghtk.returnFee(33_000);

        call(BODY).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.fee").value(33000))
                .andExpect(jsonPath("$.result.source").value("GHTK"))
                .andExpect(jsonPath("$.result.estimated").value(false))
                .andExpect(jsonPath("$.result.weightGrams").value(900))
                .andExpect(jsonPath("$.result.deliverable").value(true));
    }

    @Test
    void fallbackIsReportedAsEstimate() throws Exception {
        cartWith("endpoint-tee2", 1);

        call(BODY).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.fee").value(25000))
                .andExpect(jsonPath("$.result.source").value("TABLE"))
                .andExpect(jsonPath("$.result.estimated").value(true))
                .andExpect(jsonPath("$.result.message").value("Không kết nối được GHTK, dùng phí tạm tính"));
    }

    @Test
    void emptyCartIsRejected() throws Exception {
        call(BODY).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2008));
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        call("{\"province\":\"\",\"ward\":\"P\",\"address\":\"a\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        call("{\"province\":\"" + "p".repeat(101) + "\",\"ward\":\"P\",\"address\":\"a\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        call("{\"province\":\"Hà Nội\",\"ward\":\" \",\"address\":\"a\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        call("{\"province\":\"Hà Nội\",\"ward\":\"" + "w".repeat(101) + "\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1011));
        call("{\"province\":\"Hà Nội\",\"ward\":\"P\",\"address\":\"" + "a".repeat(301) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1011));
    }
}
