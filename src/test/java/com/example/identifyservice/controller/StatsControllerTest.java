package com.example.identifyservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatsControllerTest {
    static final String URL = "/admin/stats/overview";
    @Autowired MockMvc mvc;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void plainUserGets403() throws Exception {
        mvc.perform(get(URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGets200WithTheDocumentedShape() throws Exception {
        mvc.perform(get(URL).param("from", "2025-03-01").param("to", "2025-03-03").param("groupBy", "day").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.from").value("2025-03-01"))
                .andExpect(jsonPath("$.result.to").value("2025-03-03"))
                .andExpect(jsonPath("$.result.groupBy").value("day"))
                .andExpect(jsonPath("$.result.previousFrom").value("2025-02-26"))
                .andExpect(jsonPath("$.result.previousTo").value("2025-02-28"))
                .andExpect(jsonPath("$.result.kpis.revenue.value").value(0))
                .andExpect(jsonPath("$.result.kpis.revenue.previous").value(0))
                .andExpect(jsonPath("$.result.kpis.orders.value").exists())
                .andExpect(jsonPath("$.result.kpis.paidOrders.value").exists())
                .andExpect(jsonPath("$.result.kpis.averageOrderValue.value").exists())
                .andExpect(jsonPath("$.result.kpis.newCustomers.value").exists())
                .andExpect(jsonPath("$.result.kpis.cancelledOrders.value").exists())
                .andExpect(jsonPath("$.result.kpis.cancelRate.value").value(0.0))
                .andExpect(jsonPath("$.result.kpis.itemsSold.value").exists())
                .andExpect(jsonPath("$.result.kpis.shippingCollected.value").exists())
                .andExpect(jsonPath("$.result.kpis.profit.coverage").value(0.0))
                .andExpect(jsonPath("$.result.kpis.profit.value").value((Object) null))
                .andExpect(jsonPath("$.result.series", hasSize(3)))
                .andExpect(jsonPath("$.result.series[0].bucket").value("2025-03-01"))
                .andExpect(jsonPath("$.result.series[0].revenue").value(0))
                .andExpect(jsonPath("$.result.series[0].orders").value(0))
                .andExpect(jsonPath("$.result.series[0].paidOrders").value(0))
                .andExpect(jsonPath("$.result.series[0].profit").value((Object) null))
                .andExpect(jsonPath("$.result.statusBreakdown", hasSize(6)))
                .andExpect(jsonPath("$.result.statusBreakdown[0].status").exists())
                .andExpect(jsonPath("$.result.statusBreakdown[0].count").exists())
                .andExpect(jsonPath("$.result.paymentBreakdown", hasSize(2)))
                .andExpect(jsonPath("$.result.paymentBreakdown[0].method").exists())
                .andExpect(jsonPath("$.result.paymentBreakdown[0].orders").exists())
                .andExpect(jsonPath("$.result.paymentBreakdown[0].revenue").exists())
                .andExpect(jsonPath("$.result.topProducts").isArray())
                .andExpect(jsonPath("$.result.lowStock").isArray());
    }

    @Test
    void adminWithoutParamsGetsTheDefaultPeriod() throws Exception {
        mvc.perform(get(URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.groupBy").value("day"))
                .andExpect(jsonPath("$.result.series", hasSize(30)));
    }

    @Test
    void invalidInputsReturn400WithCode1011() throws Exception {
        String[][] bad = {
                {"2025-03-10", "2025-03-01", "day"},
                {"not-a-date", "2025-03-01", "day"},
                {"2025-03-01", "2025-02-30x", "day"},
                {"2025-03-01", "2025-03-05", "week"},
                {"2020-01-01", "2024-12-31", "month"},
                {"2024-01-01", "2025-01-01", "day"},
        };
        for (String[] b : bad) {
            mvc.perform(get(URL).param("from", b[0]).param("to", b[1]).param("groupBy", b[2]).with(admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1011));
        }
    }
}
