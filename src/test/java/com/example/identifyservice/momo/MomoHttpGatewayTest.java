package com.example.identifyservice.momo;

import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MomoHttpGatewayTest {
    static final MomoProperties PROPS = new MomoProperties("https://test-payment.momo.vn", "PC", "AK", "SK",
            "payWithMethod", "https://ipn");

    MockRestServiceServer server;
    MomoHttpGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new MomoHttpGateway(PROPS, builder);
    }

    @Test
    void createSendsSignedRequestAndReturnsPayUrl() {
        String raw = MomoSigner.createPaymentRaw("AK", 150000, "", "https://ipn", "DH1_1", "info", "PC",
                "https://redir", "REQ1", "payWithMethod");
        String expectedSignature = MomoSigner.hmacSha256Hex("SK", raw);

        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.signature").value(expectedSignature))
                .andExpect(jsonPath("$.requestType").value("payWithMethod"))
                .andExpect(jsonPath("$.amount").value(150000))
                .andExpect(jsonPath("$.orderId").value("DH1_1"))
                .andRespond(withSuccess("{\"resultCode\":0,\"message\":\"Successful.\",\"payUrl\":\"https://pay/x\"}",
                        MediaType.APPLICATION_JSON));

        var result = gateway.create(new MomoCreateCommand("DH1_1", "REQ1", 150000, "info", "https://redir", "https://ipn"));

        assertThat(result.resultCode()).isZero();
        assertThat(result.payUrl()).isEqualTo("https://pay/x");
        server.verify();
    }

    @Test
    void queryParsesResultAndSignsRequest() {
        String expected = MomoSigner.hmacSha256Hex("SK", MomoSigner.queryRaw("AK", "DH1_1", "PC", "REQ1"));
        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/query"))
                .andExpect(jsonPath("$.signature").value(expected))
                .andRespond(withSuccess("{\"resultCode\":0,\"message\":\"ok\",\"amount\":150000,\"transId\":99}",
                        MediaType.APPLICATION_JSON));

        var result = gateway.query("DH1_1", "REQ1");

        assertThat(result.resultCode()).isZero();
        assertThat(result.amount()).isEqualTo(150000);
        assertThat(result.transId()).isEqualTo(99L);
    }

    @Test
    void missingAmountBecomesMinusOne() {
        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/query"))
                .andRespond(withSuccess("{\"resultCode\":1000,\"message\":\"pending\"}", MediaType.APPLICATION_JSON));
        assertThat(gateway.query("DH1_1", "REQ1").amount()).isEqualTo(-1);
    }

    @Test
    void httpErrorBecomesGatewayError() {
        server.expect(requestTo("https://test-payment.momo.vn/v2/gateway/api/query")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.query("DH1_1", "REQ1")).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_GATEWAY_ERROR);
    }

    @Test
    void blankKeysAreReportedAsGatewayError() {
        var unconfigured = new MomoHttpGateway(new MomoProperties("https://x", "", "", "", "payWithMethod", ""),
                RestClient.builder());
        assertThatThrownBy(() -> unconfigured.query("a", "b")).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_GATEWAY_ERROR);
    }
}
