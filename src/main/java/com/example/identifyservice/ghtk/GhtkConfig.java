package com.example.identifyservice.ghtk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GhtkConfig {
    /**
     * Uses the (prototype) Boot RestClient.Builder but replaces its request factory with the short GHTK timeouts,
     * so a slow GHTK cannot hold a request thread for the global 15 s.
     */
    @Bean
    GhtkHttpGateway ghtkHttpGateway(GhtkProperties props, RestClient.Builder builder) {
        return new GhtkHttpGateway(props, builder.requestFactory(GhtkHttpGateway.timeoutRequestFactory()));
    }
}
