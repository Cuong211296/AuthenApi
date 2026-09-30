package com.example.identifyservice.momo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "momo")
public record MomoProperties(String endpoint, String partnerCode, String accessKey, String secretKey,
                             String requestType, String ipnUrl) {
}
