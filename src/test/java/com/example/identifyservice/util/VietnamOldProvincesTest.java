package com.example.identifyservice.util;

import com.example.identifyservice.entity.ShippingRate;
import com.example.identifyservice.service.ShippingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VietnamOldProvincesTest {
    @Autowired ShippingService shipping;

    @Test
    void thereAreSixtyThreeOldProvincesAndEveryTargetIsOneOfTheThirtyFourUnits() {
        assertThat(VietnamOldProvinces.oldNames()).hasSize(63);
        for (String old : VietnamOldProvinces.oldNames())
            assertThat(VietnamProvinces.ALL).as(old).contains(VietnamOldProvinces.newUnitOf(old).orElseThrow());
        assertThat(VietnamOldProvinces.oldNames().stream().map(o -> VietnamOldProvinces.newUnitOf(o).orElseThrow())
                .distinct()).hasSameSizeAs(VietnamProvinces.ALL);   // every one of the 34 units is covered
    }

    @Test
    void everyOldProvinceNameResolvesToATableRate() {
        for (String old : VietnamOldProvinces.oldNames()) {
            Optional<ShippingRate> rate = shipping.findRateByCarrierName(old);
            assertThat(rate).as(old).isPresent();
        }
    }

    @Test
    void namesAreNormalisedForCaseDiacriticsPrefixesAndPunctuation() {
        assertThat(VietnamOldProvinces.newUnitOf("Bình Dương")).contains("TP Hồ Chí Minh");
        assertThat(VietnamOldProvinces.newUnitOf("  tỉnh BÌNH dương ")).contains("TP Hồ Chí Minh");
        assertThat(VietnamOldProvinces.newUnitOf("Binh Duong")).contains("TP Hồ Chí Minh");
        assertThat(VietnamOldProvinces.newUnitOf("Bà Rịa - Vũng Tàu")).contains("TP Hồ Chí Minh");
        assertThat(VietnamOldProvinces.newUnitOf("Ba Ria Vung Tau")).contains("TP Hồ Chí Minh");
        assertThat(VietnamOldProvinces.newUnitOf("Thành phố Hồ Chí Minh")).contains("TP Hồ Chí Minh");
        assertThat(VietnamOldProvinces.newUnitOf("TP. Hồ Chí Minh")).contains("TP Hồ Chí Minh");
        assertThat(VietnamOldProvinces.newUnitOf("Thừa Thiên Huế")).contains("Huế");
        assertThat(VietnamOldProvinces.newUnitOf("Quảng Nam")).contains("Đà Nẵng");
        assertThat(VietnamOldProvinces.newUnitOf("Đăk Nông")).contains("Lâm Đồng");
        assertThat(VietnamOldProvinces.newUnitOf("Đắk Nông")).contains("Lâm Đồng");
        assertThat(VietnamOldProvinces.newUnitOf("Hà Nội")).contains("Hà Nội");
        assertThat(VietnamOldProvinces.newUnitOf("Atlantis")).isEmpty();
        assertThat(VietnamOldProvinces.newUnitOf(null)).isEmpty();
        assertThat(VietnamOldProvinces.newUnitOf("  ")).isEmpty();
        assertThat(shipping.findRateByCarrierName("Atlantis")).isEmpty();
    }
}
