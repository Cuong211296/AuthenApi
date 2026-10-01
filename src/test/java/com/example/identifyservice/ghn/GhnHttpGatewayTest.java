package com.example.identifyservice.ghn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GhnHttpGatewayTest {
    static final String TOKEN = "SECRET-GHN-TOKEN";
    static final String FEE_URL = "https://ghn.test/shiip/public-api/v2/shipping-order/fee";
    static final GhnProperties PROPS = new GhnProperties(TOKEN, "198765", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhnFeeRequest REQUEST = new GhnFeeRequest(1442, "20308", 1300, 850_000);
    static final ObjectMapper JSON = new ObjectMapper();

    MockRestServiceServer server;
    GhnHttpGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = build(PROPS);
    }

    private GhnHttpGateway build(GhnProperties props) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GhnHttpGateway(props, builder);
    }

    private static JsonNode body(ClientHttpRequest r) {
        try {
            return JSON.readTree(((MockClientHttpRequest) r).getBodyAsString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void feeIsPostedWithHeadersAndJsonBodyAndTotalIsParsed() {
        server.expect(requestTo(FEE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Token", TOKEN))
                .andExpect(header("ShopId", "198765"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(r -> {
                    JsonNode b = body(r);
                    assertThat(b.get("service_type_id").asInt()).isEqualTo(2);
                    assertThat(b.has("from_district_id")).isFalse();
                    assertThat(b.get("to_district_id").asInt()).isEqualTo(1442);
                    assertThat(b.get("to_ward_code").isTextual()).isTrue();
                    assertThat(b.get("to_ward_code").asText()).isEqualTo("20308");
                    assertThat(b.get("weight").asInt()).isEqualTo(1300);
                    assertThat(b.get("length").asInt()).isEqualTo(25);
                    assertThat(b.get("width").asInt()).isEqualTo(20);
                    assertThat(b.get("height").asInt()).isEqualTo(10);
                    assertThat(b.get("insurance_value").asLong()).isEqualTo(850_000);
                })
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":{\"total\":37000,"
                        + "\"service_fee\":37000,\"insurance_fee\":0}}", MediaType.APPLICATION_JSON));

        assertThat(gateway.calculateFee(REQUEST)).isEqualTo(new GhnFeeResult(37_000));
        server.verify();
    }

    @Test
    void fromDistrictIsSentOnlyWhenConfigured() {
        gateway = build(new GhnProperties(TOKEN, "198765", "https://ghn.test", 1485, 2, 30, 20, 15));
        server.expect(requestTo(FEE_URL))
                .andExpect(r -> {
                    JsonNode b = body(r);
                    assertThat(b.get("from_district_id").asInt()).isEqualTo(1485);
                    assertThat(b.get("length").asInt()).isEqualTo(30);
                })
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"total\":1}}", MediaType.APPLICATION_JSON));
        assertThat(gateway.calculateFee(REQUEST).total()).isEqualTo(1);
    }

    @Test
    void feeFailuresAreUnavailableAndNeverLeakTheToken() {
        String[] bodies = {"{\"code\":400,\"message\":\"bad\"}", "{\"data\":{\"total\":1}}", "not json", "[]",
                "{\"code\":200,\"data\":{}}", "{\"code\":200,\"data\":{\"total\":-5}}",
                "{\"code\":200,\"data\":{\"total\":\"abc\"}}", ""};
        for (String b : bodies) {
            gateway = build(PROPS);
            server.expect(requestTo(FEE_URL)).andRespond(withSuccess(b, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhnUnavailableException.class)
                    .satisfies(e -> assertThat(chain(e)).doesNotContain(TOKEN));
        }
    }

    @Test
    void http500AndNetworkErrorsAreUnavailableWithoutLeakingTheToken() {
        server.expect(requestTo(FEE_URL)).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhnUnavailableException.class)
                .isNotInstanceOf(GhnRejectedException.class)
                .satisfies(e -> assertThat(chain(e)).doesNotContain(TOKEN));

        gateway = build(PROPS);
        server.expect(requestTo(FEE_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhnUnavailableException.class)
                .satisfies(e -> assertThat(chain(e)).doesNotContain(TOKEN));
    }

    @Test
    void http400IsARejectionNotAnOutage() {
        server.expect(requestTo(FEE_URL))
                .andRespond(withBadRequest().body("{\"code\":400,\"message\":\"weight\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhnRejectedException.class);
    }

    @Test
    void disabledPropertiesNeverCallOut() {
        gateway = build(new GhnProperties("", "", "https://ghn.test", null, 2, 25, 20, 10));
        assertThatThrownBy(() -> gateway.calculateFee(REQUEST)).isInstanceOf(GhnUnavailableException.class);
        assertThatThrownBy(() -> gateway.provinces()).isInstanceOf(GhnUnavailableException.class);
        server.verify(); // no expectation was set, so any request would have failed
    }

    @Test
    void provincesAreReadWithGetAndTokenAndBothKeyCasingsAreAccepted() {
        server.expect(requestTo("https://ghn.test/shiip/public-api/master-data/province"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Token", TOKEN))
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":["
                        + "{\"ProvinceID\":201,\"ProvinceName\":\"Hà Nội\",\"Code\":\"4\"},"
                        + "{\"province_id\":202,\"province_name\":\"Hồ Chí Minh\"},"
                        + "{\"ProvinceID\":203}]}", MediaType.APPLICATION_JSON));

        assertThat(gateway.provinces()).containsExactly(new GhnProvince(201, "Hà Nội"),
                new GhnProvince(202, "Hồ Chí Minh"));
    }

    @Test
    void districtsArePostedWithProvinceIdBodyAndParsed() {
        server.expect(requestTo("https://ghn.test/shiip/public-api/master-data/district"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Token", TOKEN))
                .andExpect(r -> assertThat(body(r).get("province_id").asInt()).isEqualTo(202))
                .andRespond(withSuccess("{\"code\":200,\"data\":["
                        + "{\"DistrictID\":1442,\"ProvinceID\":202,\"DistrictName\":\"Quận 1\"},"
                        + "{\"district_id\":1443,\"province_id\":202,\"district_name\":\"Quận 2\"},"
                        + "{\"DistrictID\":1444,\"DistrictName\":\"Quận 3\"}]}", MediaType.APPLICATION_JSON));

        assertThat(gateway.districts(202)).containsExactly(new GhnDistrict(1442, 202, "Quận 1"),
                new GhnDistrict(1443, 202, "Quận 2"), new GhnDistrict(1444, 202, "Quận 3"));
    }

    @Test
    void wardsArePostedWithDistrictIdBodyAndCodesAreStrings() {
        server.expect(requestTo("https://ghn.test/shiip/public-api/master-data/ward"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Token", TOKEN))
                .andExpect(r -> assertThat(body(r).get("district_id").asInt()).isEqualTo(1442))
                .andRespond(withSuccess("{\"code\":200,\"data\":["
                        + "{\"WardCode\":\"20308\",\"DistrictID\":1442,\"WardName\":\"Phường Bến Nghé\"},"
                        + "{\"ward_code\":20309,\"district_id\":1442,\"ward_name\":\"Phường Bến Thành\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.wards(1442)).containsExactly(new GhnWard("20308", 1442, "Phường Bến Nghé"),
                new GhnWard("20309", 1442, "Phường Bến Thành"));
    }

    @Test
    void nullDataIsAnEmptyListButBadShapesAreUnavailable() {
        server.expect(requestTo("https://ghn.test/shiip/public-api/master-data/ward"))
                .andRespond(withSuccess("{\"code\":200,\"data\":null}", MediaType.APPLICATION_JSON));
        assertThat(gateway.wards(1)).isEmpty();

        for (String b : List.of("{\"code\":500,\"data\":[]}", "{\"code\":200,\"data\":{}}", "oops", "{}")) {
            gateway = build(PROPS);
            server.expect(requestTo("https://ghn.test/shiip/public-api/master-data/province"))
                    .andRespond(withSuccess(b, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> gateway.provinces()).isInstanceOf(GhnUnavailableException.class)
                    .satisfies(e -> assertThat(chain(e)).doesNotContain(TOKEN));
        }
        gateway = build(PROPS);
        server.expect(requestTo("https://ghn.test/shiip/public-api/master-data/district")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.districts(1)).isInstanceOf(GhnUnavailableException.class);
    }

    @Test
    void dedicatedTimeoutsAreThreeAndFiveSeconds() {
        var factory = GhnHttpGateway.timeoutRequestFactory();
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(3_000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(5_000);
    }

    private static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) sb.append(t).append('|');
        return sb.toString();
    }
}
