package com.example.identifyservice.ghn;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Real GHN client. Never logs or puts the token or customer data into messages: only the class name of the
 * failure and the HTTP status. The JSON shapes follow the public GHN guides and must be confirmed against the
 * real API (see the task report).
 */
@Slf4j
public class GhnHttpGateway implements GhnGateway {
    public static final int CONNECT_TIMEOUT_MS = 3_000;
    public static final int READ_TIMEOUT_MS = 5_000;
    static final String FEE_PATH = "/shiip/public-api/v2/shipping-order/fee";
    static final String PROVINCE_PATH = "/shiip/public-api/master-data/province";
    static final String DISTRICT_PATH = "/shiip/public-api/master-data/district";
    static final String WARD_PATH = "/shiip/public-api/master-data/ward";

    private final GhnProperties props;
    private final RestClient restClient;
    /** A refusal (HTTP 4xx) can repeat on every request, so it is logged at WARN at most once per 5 minutes. */
    private final LogThrottle refusalLog = new LogThrottle(Duration.ofMinutes(5));

    public GhnHttpGateway(GhnProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.baseUrl(props.effectiveBaseUrl()).build();
    }

    public static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    @Override
    public GhnFeeResult calculateFee(GhnFeeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service_type_id", props.serviceType());
        if (props.fromDistrictId() != null) body.put("from_district_id", props.fromDistrictId());
        body.put("to_district_id", request.toDistrictId());
        body.put("to_ward_code", request.toWardCode());
        body.put("weight", request.weightGrams());
        body.put("length", props.length());
        body.put("width", props.width());
        body.put("height", props.height());
        body.put("insurance_value", request.insuranceValue());
        JsonNode res = call("fee", () -> restClient.post().uri(FEE_PATH)
                .header("Token", props.token()).header("ShopId", props.shopId())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class), false);
        requireOk(res, false);
        JsonNode total = res.path("data").path("total");
        if (!total.isIntegralNumber() || total.asLong() < 0)
            throw new GhnUnavailableException("GHN answered without a valid fee amount");
        return new GhnFeeResult(total.asLong());
    }

    @Override
    public List<GhnProvince> provinces() {
        JsonNode res = call("province list", () -> restClient.get().uri(PROVINCE_PATH)
                .header("Token", props.token()).retrieve().body(JsonNode.class), true);
        List<GhnProvince> out = new ArrayList<>();
        for (JsonNode n : dataArray(res)) {
            Integer id = intOf(n, "ProvinceID", "province_id");
            String name = textOf(n, "ProvinceName", "province_name");
            if (id != null && name != null) out.add(new GhnProvince(id, name));
        }
        return out;
    }

    @Override
    public List<GhnDistrict> districts(int provinceId) {
        JsonNode res = call("district list", () -> restClient.post().uri(DISTRICT_PATH)
                .header("Token", props.token()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("province_id", provinceId)).retrieve().body(JsonNode.class), true);
        List<GhnDistrict> out = new ArrayList<>();
        for (JsonNode n : dataArray(res)) {
            Integer id = intOf(n, "DistrictID", "district_id");
            String name = textOf(n, "DistrictName", "district_name");
            Integer province = intOf(n, "ProvinceID", "province_id");
            if (id != null && name != null)
                out.add(new GhnDistrict(id, province == null ? provinceId : province, name));
        }
        return out;
    }

    @Override
    public List<GhnWard> wards(int districtId) {
        JsonNode res = call("ward list", () -> restClient.post().uri(WARD_PATH)
                .header("Token", props.token()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("district_id", districtId)).retrieve().body(JsonNode.class), true);
        List<GhnWard> out = new ArrayList<>();
        for (JsonNode n : dataArray(res)) {
            String code = textOf(n, "WardCode", "ward_code");
            String name = textOf(n, "WardName", "ward_name");
            Integer district = intOf(n, "DistrictID", "district_id");
            if (code != null && name != null)
                out.add(new GhnWard(code, district == null ? districtId : district, name));
        }
        return out;
    }

    /** Runs the HTTP call; every failure becomes a GhnUnavailableException carrying no URL, token or body. */
    private JsonNode call(String what, Supplier<JsonNode> request, boolean masterData) {
        if (!props.isEnabled()) throw new GhnUnavailableException("GHN is not configured");
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // A refusal concerns this one query (fee: HTTP 400; master data: any 4xx except 429), not GHN as a whole.
            boolean refusal = masterData ? status >= 400 && status < 500 && status != 429
                    : status == HttpStatus.BAD_REQUEST.value();
            if (!refusal || refusalLog.allow(System.currentTimeMillis()))
                log.warn("GHN {} call failed: {} (HTTP {})", what, e.getClass().getSimpleName(), status);
            if (refusal) throw new GhnRejectedException("GHN rejected the " + what + " query");
            throw new GhnUnavailableException("GHN " + what + " call failed (HTTP " + status + ")");
        } catch (RuntimeException e) {
            log.warn("GHN {} call failed: {}", what, e.getClass().getSimpleName());
            throw new GhnUnavailableException("GHN " + what + " call failed: " + e.getClass().getSimpleName());
        }
    }

    private static void requireOk(JsonNode res, boolean refuseBadCode) {
        if (res == null || !res.isObject() || !res.path("code").isIntegralNumber())
            throw new GhnUnavailableException("GHN answered an unexpected body");
        if (res.get("code").asInt() != 200) {
            if (refuseBadCode) throw new GhnRejectedException("GHN answered code " + res.get("code").asInt());
            throw new GhnUnavailableException("GHN answered code " + res.get("code").asInt());
        }
    }

    /** {@code data} may be null for an id without children: that is an empty list, not an outage. */
    private static List<JsonNode> dataArray(JsonNode res) {
        requireOk(res, true);
        JsonNode data = res.get("data");
        if (data == null || data.isNull()) return List.of();
        if (!data.isArray()) throw new GhnUnavailableException("GHN answered an unexpected list");
        List<JsonNode> out = new ArrayList<>();
        data.forEach(n -> {
            if (n.isObject()) out.add(n);
        });
        return out;
    }

    private static JsonNode first(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) return v;
        }
        return null;
    }

    private static Integer intOf(JsonNode n, String... keys) {
        JsonNode v = first(n, keys);
        return v != null && v.isIntegralNumber() && v.canConvertToInt() ? v.asInt() : null;
    }

    private static String textOf(JsonNode n, String... keys) {
        JsonNode v = first(n, keys);
        if (v == null || !(v.isTextual() || v.isNumber())) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }
}
