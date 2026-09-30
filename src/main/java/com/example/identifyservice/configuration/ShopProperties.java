package com.example.identifyservice.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop")
public record ShopProperties(String adminPassword, String mailFrom, String frontendUrl, int orderExpiryMinutes) {
}
