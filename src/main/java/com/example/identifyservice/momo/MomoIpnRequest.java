package com.example.identifyservice.momo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MomoIpnRequest(String partnerCode, String orderId, String requestId, long amount, String orderInfo,
                             String orderType, long transId, int resultCode, String message, String payType,
                             long responseTime, String extraData, String signature) {
}
