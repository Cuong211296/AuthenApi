package com.example.identifyservice.momo;

public record MomoCreateCommand(String providerOrderId, String requestId, long amount, String orderInfo,
                                String redirectUrl, String ipnUrl) {
}
