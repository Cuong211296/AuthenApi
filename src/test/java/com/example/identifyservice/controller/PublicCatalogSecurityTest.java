package com.example.identifyservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicCatalogSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void productsAndCategoriesArePublic() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(get("/categories")).andExpect(status().isOk());
    }

    @Test
    void missingProductReturns404NotAnAuthError() throws Exception {
        mvc.perform(get("/products/nope")).andExpect(status().isNotFound());
    }
}
