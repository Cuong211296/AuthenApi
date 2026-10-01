package com.example.identifyservice.ghtk;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class GhtkHttpGateway implements GhtkGateway {
    private final GhtkProperties props;
    private final RestClient restClient;

    public GhtkHttpGateway(GhtkProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder
                .baseUrl(GhtkProperties.blank(props.baseUrl()) ? "https://services.giaohangtietkiem.vn" : props.baseUrl())
                .build();
    }

    @Override
    public GhtkFeeResult calculateFee(GhtkFeeRequest request) {
        if (!props.isEnabled()) throw new GhtkUnavailableException("GHTK is not configured");
        try {
            JsonNode res = restClient.get()
                    .uri(uri -> {
                        // values go in as URI template variables so they are percent-encoded as UTF-8 strictly
                        Map<String, Object> vars = new HashMap<>();
                        uri.path("/services/shipment/fee");
                        param(uri, vars, "pick_province", props.pickProvince());
                        param(uri, vars, "pick_ward", props.pickWard());
                        if (!GhtkProperties.blank(props.pickDistrict()))
                            param(uri, vars, "pick_district", props.pickDistrict());
                        if (!GhtkProperties.blank(props.pickAddress()))
                            param(uri, vars, "pick_address", props.pickAddress());
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
            // never log the request (it carries the token header); only the failure type and its message
            log.warn("GHTK fee call failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
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
        if (!fee.path("fee").isIntegralNumber())
            throw new GhtkUnavailableException("GHTK answered without a fee amount");
        return new GhtkFeeResult(true, true, fee.get("fee").asLong(), message);
    }
}
