package com.example.identifyservice.ghn;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GhnConfig {
    /** Own request factory with the short GHN timeouts (connect 3 s, read 5 s), not the global ones. */
    @Bean
    GhnHttpGateway ghnHttpGateway(GhnProperties props, RestClient.Builder builder) {
        return new GhnHttpGateway(props, builder.requestFactory(GhnHttpGateway.timeoutRequestFactory()));
    }
}
