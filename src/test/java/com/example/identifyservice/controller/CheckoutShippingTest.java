package com.example.identifyservice.controller;

import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.ghtk.GhtkFeeResult;
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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CheckoutShippingTest {
    @Autowired MockMvc mvc;
    @Autowired TestDataFactory data;
    @Autowired CartRepository carts;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingQuoteService quoteService;

    @BeforeEach
    void setUp() {
        ghtk.reset();
        quoteService.clearCache();
    }

    private void cartWith(String slug) {
        ProductVariant v = data.variant(data.product(slug, 200_000, true), "M", "white", 9, null);
        Cart cart = Cart.builder().user(data.user("buyer")).build();
        cart.getItems().add(CartItem.builder().cart(cart).variant(v).quantity(1).build());
        carts.save(cart);
    }

    private ResultActions checkout(String province, String extraJson) throws Exception {
        return mvc.perform(post("/orders")
                .with(jwt().jwt(j -> j.subject("buyer")).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverName\":\"A\",\"phone\":\"0901234567\",\"email\":\"a@b.co\","
                        + "\"address\":\"1 St\",\"province\":\"" + province + "\",\"ward\":\"Phường 1\","
                        + "\"paymentMethod\":\"COD\"" + extraJson + "}"));
    }

    @Test
    void clientSuppliedShippingFeeIsIgnored() throws Exception {
        cartWith("fee-tee");
        ghtk.returnFee(31_000);

        checkout("Hà Nội", ",\"shippingFee\":0,\"total\":1,\"shippingSource\":\"TABLE\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.shippingFee").value(31000))
                .andExpect(jsonPath("$.result.total").value(231000))
                .andExpect(jsonPath("$.result.shippingSource").value("GHTK"));
    }

    @Test
    void undeliverableAddressGets400WithTheDedicatedCode() throws Exception {
        cartWith("fee-tee2");
        ghtk.returnResult(new GhtkFeeResult(true, false, 0, null));

        checkout("Atlantis", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2018));
    }
}
