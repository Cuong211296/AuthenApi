package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.ShippingRateRequest;
import com.example.identifyservice.dto.response.ShippingRateResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShippingServiceTest {
    @Autowired ShippingService shipping;

    @Test
    void allProvincesAreSeeded() {
        assertThat(shipping.list()).hasSize(34);
        assertThat(shipping.list()).extracting(ShippingRateResponse::province).contains("Hà Nội", "Đà Nẵng");
    }

    @Test
    void lookupIgnoresCaseAndSurroundingSpaces() {
        assertThat(shipping.fee("  hà nội ").fee()).isEqualTo(25_000);
        assertThat(shipping.fee("Lào Cai").fee()).isEqualTo(35_000);
    }

    @Test
    void unknownOrBlankProvinceIsRejected() {
        for (String p : new String[]{"Atlantis", "", "   ", null}) {
            assertThatThrownBy(() -> shipping.requireRate(p))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_PROVINCE);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanChangeFeeAndAddProvince() {
        var hanoi = shipping.list().stream().filter(r -> r.province().equals("Hà Nội")).findFirst().orElseThrow();
        shipping.updateFee(hanoi.id(), 30_000);
        assertThat(shipping.fee("Hà Nội").fee()).isEqualTo(30_000);

        shipping.create(new ShippingRateRequest("Khu Test", 50_000));
        assertThat(shipping.fee("khu test").fee()).isEqualTo(50_000);
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminCannotEditRates() {
        assertThatThrownBy(() -> shipping.create(new ShippingRateRequest("X", 1)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateProvinceIsRejectedWithProvinceExisted() {
        assertThatThrownBy(() -> shipping.create(new ShippingRateRequest(" hà nội ", 1)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.PROVINCE_EXISTED);
    }
}
