package com.example.identifyservice.ghn;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"ghn.from-district-id=", "ghn.service-type-id=", "ghn.default-length="})
@ActiveProfiles("test")
class GhnWiringTest {
    @Autowired ApplicationContext context;
    @Autowired GhnProperties props;

    @Test
    void productionGatewayBeanIsBuiltWithTheDedicatedShortTimeouts() {
        GhnHttpGateway gateway = context.getBean("ghnHttpGateway", GhnHttpGateway.class);
        RestClient client = (RestClient) ReflectionTestUtils.getField(gateway, "restClient");
        Object factory = ReflectionTestUtils.getField(client, "clientRequestFactory");
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(3_000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(5_000);
    }

    @Test
    void blankOptionalSettingsBindToDefaultsAndNeverBlockStartup() {
        assertThat(props.isEnabled()).isTrue();                  // test profile sets a token and a shop id
        assertThat(props.fromDistrictId()).isNull();
        assertThat(props.serviceType()).isEqualTo(2);
        assertThat(props.length()).isEqualTo(25);
        assertThat(props.width()).isEqualTo(20);
        assertThat(props.height()).isEqualTo(10);
    }

    @Test
    void enabledOnlyWhenBothTokenAndShopIdAreSet() {
        assertThat(new GhnProperties("t", "", null, null, null, null, null, null).isEnabled()).isFalse();
        assertThat(new GhnProperties("", "1", null, null, null, null, null, null).isEnabled()).isFalse();
        assertThat(new GhnProperties(null, null, null, null, null, null, null, null).isEnabled()).isFalse();
        assertThat(new GhnProperties("t", "1", null, null, null, null, null, null).isEnabled()).isTrue();
        assertThat(new GhnProperties("t", "1", null, null, null, null, null, null).effectiveBaseUrl())
                .isEqualTo("https://dev-online-gateway.ghn.vn");
    }
}
