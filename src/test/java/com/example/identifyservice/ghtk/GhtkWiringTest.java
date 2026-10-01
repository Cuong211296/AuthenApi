package com.example.identifyservice.ghtk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GhtkWiringTest {
    @Autowired ApplicationContext context;

    @Test
    void productionGatewayBeanIsBuiltWithTheDedicatedShortTimeouts() {
        GhtkHttpGateway gateway = context.getBean("ghtkHttpGateway", GhtkHttpGateway.class);
        RestClient client = (RestClient) ReflectionTestUtils.getField(gateway, "restClient");
        Object factory = ReflectionTestUtils.getField(client, "clientRequestFactory");
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(3_000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(5_000);
    }
}
