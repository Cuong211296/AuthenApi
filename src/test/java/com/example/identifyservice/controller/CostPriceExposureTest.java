package com.example.identifyservice.controller;

import com.example.identifyservice.dto.response.OrderItemResponse;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.dto.response.ProductSummaryResponse;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.repository.ProductRepository;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CostPriceExposureTest {
    @Autowired MockMvc mvc;
    @Autowired TestDataFactory data;
    @Autowired ProductRepository products;

    private Product productWithCost(String slug) {
        Product p = data.product(slug, 200_000, true);
        data.variant(p, "M", "white", 5, null);
        p.setCostPrice(70_000L);
        return products.save(p);
    }

    @Test
    void publicDetailAndListNeverContainCostPrice() throws Exception {
        productWithCost("secret-cost-tee");

        String detail = mvc.perform(get("/products/secret-cost-tee")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.slug").value("secret-cost-tee"))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain("costPrice").doesNotContain("70000");

        String list = mvc.perform(get("/products")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("secret-cost-tee").doesNotContain("costPrice").doesNotContain("70000");
    }

    @Test
    void adminDetailContainsCostPrice() throws Exception {
        Product p = productWithCost("admin-cost-tee");
        mvc.perform(get("/admin/products/" + p.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.costPrice").value(70000));
    }

    @Test
    void summaryCartAndOrderResponsesHaveNoCostProperty() {
        assertThat(componentNames(ProductSummaryResponse.class)).doesNotContain("costPrice", "unitCost");
        assertThat(componentNames(OrderItemResponse.class)).doesNotContain("costPrice", "unitCost");
        assertThat(componentNames(OrderResponse.class)).doesNotContain("costPrice", "unitCost");
        assertThat(componentNames(com.example.identifyservice.dto.response.CartResponse.class))
                .doesNotContain("costPrice", "unitCost");
        assertThat(componentNames(com.example.identifyservice.dto.response.CartItemResponse.class))
                .doesNotContain("costPrice", "unitCost");
    }

    private static java.util.List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(c -> c.getName()).toList();
    }
}
