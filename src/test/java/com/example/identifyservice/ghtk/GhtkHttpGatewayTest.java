package com.example.identifyservice.ghtk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GhtkHttpGatewayTest {
    static final GhtkProperties PROPS = new GhtkProperties("SECRET-TOKEN", "SRC", "https://ghtk.test",
            "Hà Nội", "Phường Test", null, "", "road");
    static final GhtkFeeRequest REQUEST = new GhtkFeeRequest("Hà Nội", "Phường Bến Nghé", "12 Nguyễn Huệ & Lê Lợi", 1300, 850_000);

    MockRestServiceServer server;
    GhtkHttpGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GhtkHttpGateway(PROPS, builder);
    }

    private static Map<String, String> decodedQuery(java.net.URI uri) {
        Map<String, String> out = new LinkedHashMap<>();
        UriComponentsBuilder.fromUri(uri).build().getQueryParams().forEach((k, v) ->
                out.put(k, UriUtils.decode(v.get(0), StandardCharsets.UTF_8)));
        return out;
    }

    @Test
    void sendsGetWithHeadersAndUtf8EncodedQuery() {
        server.expect(r -> {
                    assertThat(r.getURI().getScheme() + "://" + r.getURI().getHost()).isEqualTo("https://ghtk.test");
                    assertThat(r.getURI().getPath()).isEqualTo("/services/shipment/fee");
                    String raw = r.getURI().getRawQuery();
                    assertThat(raw).contains("province=H%C3%A0%20N%E1%BB%99i");
                    assertThat(raw).contains("address=12%20Nguy%E1%BB%85n%20Hu%E1%BB%87%20%26%20L%C3%AA%20L%E1%BB%A3i");
                    assertThat(decodedQuery(r.getURI())).containsOnly(
                            Map.entry("pick_province", "Hà Nội"), Map.entry("pick_ward", "Phường Test"),
                            Map.entry("province", "Hà Nội"), Map.entry("ward", "Phường Bến Nghé"),
                            Map.entry("address", "12 Nguyễn Huệ & Lê Lợi"), Map.entry("weight", "1300"),
                            Map.entry("value", "850000"), Map.entry("transport", "road"));
                })
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Token", "SECRET-TOKEN"))
                .andExpect(header("X-Client-Source", "SRC"))
                .andRespond(withSuccess("{\"success\":true,\"fee\":{\"name\":\"area1\",\"fee\":30400,"
                        + "\"insurance_fee\":15000,\"delivery\":true}}", MediaType.APPLICATION_JSON));

        GhtkFeeResult result = gateway.calculateFee(REQUEST);

        assertThat(result).isEqualTo(new GhtkFeeResult(true, true, 30_400, null));
        server.verify();
    }

    @Test
    void optionalPickFieldsAreSentWhenConfigured() {
        var props = new GhtkProperties("T", "SRC", "https://ghtk.test", "Hà Nội", "Phường Test", "Quận Test",
                "1 Kho", "fly");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer s = MockRestServiceServer.bindTo(builder).build();
        s.expect(r -> assertThat(decodedQuery(r.getURI())).containsEntry("pick_district", "Quận Test")
                        .containsEntry("pick_address", "1 Kho").containsEntry("transport", "fly"))
                .andRespond(withSuccess("{\"success\":true,\"fee\":{\"fee\":1,\"delivery\":true}}", MediaType.APPLICATION_JSON));
        new GhtkHttpGateway(props, builder).calculateFee(REQUEST);
        s.verify();
    }

    @Test
    void deliveryFalseIsReportedAsNotDeliverable() {
        server.expect(r -> {}).andRespond(withSuccess("{\"success\":true,\"fee\":{\"fee\":0,\"delivery\":false}}",
                MediaType.APPLICATION_JSON));
        GhtkFeeResult result = gateway.calculateFee(REQUEST);
        assertThat(result.success()).isTrue();
        assertThat(result.deliverable()).isFalse();
    }

    @Test
    void successFalseCarriesTheMessage() {
        server.expect(r -> {}).andRespond(withSuccess("{\"success\":false,\"message\":\"Địa chỉ không hợp lệ\"}",
                MediaType.APPLICATION_JSON));
        GhtkFeeResult result = gateway.calculateFee(REQUEST);
        assertThat(result).isEqualTo(new GhtkFeeResult(false, false, 0, "Địa chỉ không hợp lệ"));
    }

    @Test
    void httpErrorMalformedJsonMissingFieldsAndTimeoutAreUnavailable() {
        server.expect(r -> {}).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhtkUnavailableException.class);

        server.reset();
        server.expect(r -> {}).andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhtkUnavailableException.class);

        server.reset();
        server.expect(r -> {}).andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhtkUnavailableException.class);

        server.reset();
        server.expect(r -> {}).andRespond(withSuccess("{\"success\":true,\"fee\":{\"fee\":\"abc\",\"delivery\":true}}",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhtkUnavailableException.class);

        server.reset();
        server.expect(r -> {}).andRespond(withException(new SocketTimeoutException("read timed out")));
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhtkUnavailableException.class);
    }

    @Test
    void unconfiguredGatewayIsUnavailableAndErrorsNeverContainTheToken() {
        var off = new GhtkHttpGateway(new GhtkProperties("", "", "https://x", "", "", "", "", "road"), RestClient.builder());
        assertThatThrownBy(() -> off.calculateFee(REQUEST)).isInstanceOf(GhtkUnavailableException.class);

        server.expect(r -> {}).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST))
                .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain("SECRET-TOKEN"));
    }

    @Test
    void propertiesEnabledOnlyWhenAllRequiredFieldsAreSet() {
        assertThat(PROPS.isEnabled()).isTrue();
        assertThat(new GhtkProperties("", "SRC", "u", "p", "w", null, null, "road").isEnabled()).isFalse();
        assertThat(new GhtkProperties("T", " ", "u", "p", "w", null, null, "road").isEnabled()).isFalse();
        assertThat(new GhtkProperties("T", "S", "u", null, "w", null, null, "road").isEnabled()).isFalse();
        assertThat(new GhtkProperties("T", "S", "u", "p", "", null, null, "road").isEnabled()).isFalse();
    }
}
