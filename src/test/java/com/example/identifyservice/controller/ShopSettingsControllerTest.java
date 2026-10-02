package com.example.identifyservice.controller;

import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.repository.ShopSettingsRepository;
import com.example.identifyservice.service.ShopSettingsService;
import com.example.identifyservice.testsupport.FakeGhnGateway;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopSettingsControllerTest {
    static final String URL = "/admin/settings/shop";
    static final String CARRIERS = "/admin/settings/carriers";
    static final String BODY = """
            {"shopName":"Quini Bear","phone":"0901234567",
             "pickup":{"provinceId":202,"districtId":1442,"wardCode":"20308","address":"12 Nguyễn Huệ"}}""";

    @Autowired MockMvc mvc;
    @Autowired FakeGhnGateway ghn;
    @Autowired ShopSettingsRepository repository;
    @Autowired ShopSettingsService service;
    @Autowired GhnMasterDataService masterData;

    @BeforeEach
    void setUp() {
        ghn.reset();
        masterData.clear();
        ghn.useSampleData();
        repository.deleteAll();
        service.invalidateSnapshot();
    }

    @AfterEach
    void tearDown() {
        ghn.reset();
        masterData.clear();
        repository.deleteAll();
        service.invalidateSnapshot();
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("boss")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
    }

    @Test
    void plainUserGets403() throws Exception {
        var user = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
        mvc.perform(get(URL).with(user)).andExpect(status().isForbidden());
        mvc.perform(put(URL).with(user).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    @Test
    void adminGetsAnEmptyShapeBeforeAnySettings() throws Exception {
        mvc.perform(get(URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.shopName").doesNotExist())
                .andExpect(jsonPath("$.result.pickup").exists())
                .andExpect(jsonPath("$.result.carriers.ghn.enabled").value(true))
                .andExpect(jsonPath("$.result.carriers.ghn.shopId").value("123456"))
                .andExpect(jsonPath("$.result.carriers.ghtk.enabled").value(true))
                .andExpect(jsonPath("$.result.addressMode").value("GHN_IDS"));
    }

    @Test
    void putSavesAndReturnsTheShapeWithMasterDataNames() throws Exception {
        mvc.perform(put(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.shopName").value("Quini Bear"))
                .andExpect(jsonPath("$.result.phone").value("0901234567"))
                .andExpect(jsonPath("$.result.pickup.provinceId").value(202))
                .andExpect(jsonPath("$.result.pickup.districtId").value(1442))
                .andExpect(jsonPath("$.result.pickup.wardCode").value("20308"))
                .andExpect(jsonPath("$.result.pickup.provinceName").value("Hồ Chí Minh"))
                .andExpect(jsonPath("$.result.pickup.districtName").value("Quận 1"))
                .andExpect(jsonPath("$.result.pickup.wardName").value("Phường Bến Nghé"))
                .andExpect(jsonPath("$.result.pickup.address").value("12 Nguyễn Huệ"))
                .andExpect(jsonPath("$.result.updatedBy").value("boss"))
                .andExpect(jsonPath("$.result.updatedAt").exists());

        mvc.perform(get(URL).with(admin()))
                .andExpect(jsonPath("$.result.shopName").value("Quini Bear"))
                .andExpect(jsonPath("$.result.pickup.wardName").value("Phường Bến Nghé"));
    }

    @Test
    void invalidInputIs400WithTheInvalidInputCode() throws Exception {
        String badWard = BODY.replace("20308", "NOPE");
        mvc.perform(put(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(badWard))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1011));
        String noName = BODY.replace("Quini Bear", " ");
        mvc.perform(put(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(noName))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1011));
        String badPhone = BODY.replace("0901234567", "123");
        mvc.perform(put(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(badPhone))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1011));
        mvc.perform(put(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shopName\":\"S\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1011));
    }

    @Test
    void masterDataDownIs502() throws Exception {
        ghn.masterDataDown();
        mvc.perform(put(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value(2019));
    }

    @Test
    void theShopIdIsShownButNoTokenEverAppears() throws Exception {
        String put = mvc.perform(put(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.carriers.ghn.shopId").value("123456"))
                .andReturn().getResponse().getContentAsString();
        String get = mvc.perform(get(URL).with(admin())).andExpect(content().contentType("application/json"))
                .andReturn().getResponse().getContentAsString();
        for (String body : new String[]{put, get}) {
            assertThat(body).doesNotContain("TEST-GHN-TOKEN").doesNotContain("TEST-TOKEN").doesNotContain("TESTSRC")
                    .doesNotContainIgnoringCase("token");
        }
    }

    @Test
    void carrierSwitchesAreAdminOnly() throws Exception {
        String body = "{\"ghn\":false,\"ghtk\":true}";
        mvc.perform(put(CARRIERS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        var user = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
        mvc.perform(put(CARRIERS).with(user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTurnsACarrierOffAndGetShowsIt() throws Exception {
        mvc.perform(put(CARRIERS).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ghn\":false,\"ghtk\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.carriers.ghn.enabled").value(false))
                .andExpect(jsonPath("$.result.carriers.ghtk.enabled").value(true))
                .andExpect(jsonPath("$.result.updatedBy").value("boss"));
        mvc.perform(get(URL).with(admin()))
                .andExpect(jsonPath("$.result.carriers.ghn.enabled").value(false))
                .andExpect(jsonPath("$.result.carriers.ghn.configured").value(true));
    }

    @Test
    void aMissingSwitchIsInvalidInput() throws Exception {
        mvc.perform(put(CARRIERS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"ghn\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
    }
}
