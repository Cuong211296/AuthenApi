package com.example.identifyservice.momo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

public final class MomoSigner {
    private MomoSigner() {}

    public static String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute HMAC", e);
        }
    }

    public static String createPaymentRaw(String accessKey, long amount, String extraData, String ipnUrl,
                                          String orderId, String orderInfo, String partnerCode,
                                          String redirectUrl, String requestId, String requestType) {
        return "accessKey=" + accessKey + "&amount=" + amount + "&extraData=" + extraData + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId + "&orderInfo=" + orderInfo + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl + "&requestId=" + requestId + "&requestType=" + requestType;
    }

    public static String queryRaw(String accessKey, String orderId, String partnerCode, String requestId) {
        return "accessKey=" + accessKey + "&orderId=" + orderId + "&partnerCode=" + partnerCode
                + "&requestId=" + requestId;
    }

    public static String ipnRaw(String accessKey, MomoIpnRequest r) {
        return "accessKey=" + accessKey + "&amount=" + r.amount() + "&extraData=" + nz(r.extraData())
                + "&message=" + nz(r.message()) + "&orderId=" + nz(r.orderId()) + "&orderInfo=" + nz(r.orderInfo())
                + "&orderType=" + nz(r.orderType()) + "&partnerCode=" + nz(r.partnerCode())
                + "&payType=" + nz(r.payType()) + "&requestId=" + nz(r.requestId())
                + "&responseTime=" + r.responseTime() + "&resultCode=" + r.resultCode() + "&transId=" + r.transId();
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String nz(String s) {
        return Objects.toString(s, "");
    }
}
