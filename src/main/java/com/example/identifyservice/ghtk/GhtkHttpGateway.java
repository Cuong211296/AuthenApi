package com.example.identifyservice.ghtk;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.util.HashMap;
import java.util.Map;

/** Registered as a bean in {@link GhtkConfig}, which gives it a RestClient with GHTK-specific timeouts. */
@Slf4j
public class GhtkHttpGateway implements GhtkGateway {
    public static final int CONNECT_TIMEOUT_MS = 3_000;
    public static final int READ_TIMEOUT_MS = 5_000;

    private final GhtkProperties props;
    private final RestClient restClient;

    public GhtkHttpGateway(GhtkProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder
                .baseUrl(GhtkProperties.blank(props.baseUrl()) ? "https://services.giaohangtietkiem.vn" : props.baseUrl())
                .build();
    }

    /** Request factory with the short GHTK timeouts (not the global ones). */
    public static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    @Override
    public GhtkFeeResult calculateFee(GhtkFeeRequest request) {
        if (!props.credentialsConfigured()) throw new GhtkUnavailableException("GHTK is not configured");
        try {
            JsonNode res = restClient.get()
                    .uri(uri -> {
                        // values go in as URI template variables so they are percent-encoded as UTF-8 strictly
                        Map<String, Object> vars = new HashMap<>();
                        uri.path("/services/shipment/fee");
                        // the pick-up comes either wholly from the request (shop settings) or wholly from properties
                        boolean fromRequest = !GhtkProperties.blank(request.pickProvince())
                                && !GhtkProperties.blank(request.pickWard());
                        String pickProvince = fromRequest ? request.pickProvince() : props.pickProvince();
                        String pickWard = fromRequest ? request.pickWard() : props.pickWard();
                        String pickDistrict = fromRequest ? request.pickDistrict() : props.pickDistrict();
                        String pickAddress = fromRequest ? request.pickAddress() : props.pickAddress();
                        param(uri, vars, "pick_province", pickProvince);
                        param(uri, vars, "pick_ward", pickWard);
                        if (!GhtkProperties.blank(pickDistrict)) param(uri, vars, "pick_district", pickDistrict);
                        if (!GhtkProperties.blank(pickAddress)) param(uri, vars, "pick_address", pickAddress);
                        param(uri, vars, "province", GhtkNames.province(request.province()));
                        param(uri, vars, "ward", request.ward());
                        param(uri, vars, "address", request.address() == null ? "" : request.address());
                        param(uri, vars, "weight", request.weightGrams());
                        param(uri, vars, "value", request.value());
                        param(uri, vars, "transport",
                                GhtkProperties.blank(props.transport()) ? "road" : props.transport());
                        return uri.build(vars);
                    })
                    .header("Token", props.token())
                    .header("X-Client-Source", props.clientSource())
                    .retrieve().body(JsonNode.class);
            return parse(res);
        } catch (GhtkUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Never log the message: it can contain the request URL (customer address). Class name and status only.
            String detail = e instanceof RestClientResponseException r ? " (HTTP " + r.getStatusCode().value() + ")" : "";
            log.warn("GHTK fee call failed: {}{}", e.getClass().getSimpleName(), detail);
            throw new GhtkUnavailableException("GHTK fee call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private static void param(UriBuilder uri, Map<String, Object> vars, String name, Object value) {
        vars.put(name, value);
        uri.queryParam(name, "{" + name + "}");
    }

    private static GhtkFeeResult parse(JsonNode res) {
        if (res == null || !res.isObject() || !res.path("success").isBoolean())
            throw new GhtkUnavailableException("GHTK answered an unexpected body");
        String message = res.hasNonNull("message") ? res.get("message").asText() : null;
        if (!res.get("success").asBoolean()) return new GhtkFeeResult(false, false, 0, message);

        JsonNode fee = res.path("fee");
        if (!fee.isObject() || !fee.path("delivery").isBoolean())
            throw new GhtkUnavailableException("GHTK answered without fee details");
        if (!fee.get("delivery").asBoolean()) return new GhtkFeeResult(true, false, 0, message);
        if (!fee.path("fee").isIntegralNumber() || fee.get("fee").asLong() < 0)
            throw new GhtkUnavailableException("GHTK answered without a valid fee amount");
        return new GhtkFeeResult(true, true, fee.get("fee").asLong(), message);
    }
}
