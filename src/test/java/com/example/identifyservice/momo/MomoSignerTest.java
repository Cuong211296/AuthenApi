package com.example.identifyservice.momo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MomoSignerTest {
    @Test
    void hmacSha256MatchesRfc4231Vector() {
        assertThat(MomoSigner.hmacSha256Hex("Jefe", "what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void createRawStringIsAlphabeticalAndExact() {
        String raw = MomoSigner.createPaymentRaw("AK", 150000, "", "https://ipn", "DH1_1", "info", "PC",
                "https://redir", "REQ1", "payWithMethod");
        assertThat(raw).isEqualTo("accessKey=AK&amount=150000&extraData=&ipnUrl=https://ipn&orderId=DH1_1"
                + "&orderInfo=info&partnerCode=PC&redirectUrl=https://redir&requestId=REQ1&requestType=payWithMethod");
    }

    @Test
    void queryRawStringIsExact() {
        assertThat(MomoSigner.queryRaw("AK", "DH1_1", "PC", "REQ1"))
                .isEqualTo("accessKey=AK&orderId=DH1_1&partnerCode=PC&requestId=REQ1");
    }

    @Test
    void ipnRawStringIsExactAndNullSafe() {
        var ipn = new MomoIpnRequest("PC", "DH1_1", "REQ1", 150000, "info", "momo_wallet", 4001, 0, "Success",
                "qr", 1700000000000L, null, "sig");
        assertThat(MomoSigner.ipnRaw("AK", ipn)).isEqualTo("accessKey=AK&amount=150000&extraData=&message=Success"
                + "&orderId=DH1_1&orderInfo=info&orderType=momo_wallet&partnerCode=PC&payType=qr&requestId=REQ1"
                + "&responseTime=1700000000000&resultCode=0&transId=4001");
    }

    @Test
    void constantTimeEqualsHandlesNullAndMismatch() {
        assertThat(MomoSigner.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(MomoSigner.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(MomoSigner.constantTimeEquals("abc", null)).isFalse();
        assertThat(MomoSigner.constantTimeEquals(null, null)).isFalse();
    }
}
