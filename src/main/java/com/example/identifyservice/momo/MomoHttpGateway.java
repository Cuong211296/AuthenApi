package com.example.identifyservice.momo;

import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class MomoHttpGateway implements MomoGateway {
    private final MomoProperties props;
    private final RestClient restClient;

    public MomoHttpGateway(MomoProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.baseUrl(props.endpoint() == null ? "" : props.endpoint()).build();
    }

    @Override
    public MomoCreateResult create(MomoCreateCommand c) {
        requireConfigured();
        String raw = MomoSigner.createPaymentRaw(props.accessKey(), c.amount(), "", c.ipnUrl(), c.providerOrderId(),
                c.orderInfo(), props.partnerCode(), c.redirectUrl(), c.requestId(), props.requestType());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", props.partnerCode());
        body.put("partnerName", "Clothing Shop");
        body.put("storeId", "ClothingShop");
        body.put("requestId", c.requestId());
        body.put("amount", c.amount());
        body.put("orderId", c.providerOrderId());
        body.put("orderInfo", c.orderInfo());
        body.put("redirectUrl", c.redirectUrl());
        body.put("ipnUrl", c.ipnUrl());
        body.put("lang", "vi");
        body.put("requestType", props.requestType());
        body.put("autoCapture", true);
        body.put("extraData", "");
        body.put("signature", MomoSigner.hmacSha256Hex(props.secretKey(), raw));

        JsonNode res = post("/v2/gateway/api/create", body);
        return new MomoCreateResult(res.path("resultCode").asInt(-1), res.path("message").asText(""),
                res.path("payUrl").asText(""));
    }

    @Override
    public MomoQueryResult query(String providerOrderId, String requestId) {
        requireConfigured();
        String raw = MomoSigner.queryRaw(props.accessKey(), providerOrderId, props.partnerCode(), requestId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", props.partnerCode());
        body.put("requestId", requestId);
        body.put("orderId", providerOrderId);
        body.put("lang", "vi");
        body.put("signature", MomoSigner.hmacSha256Hex(props.secretKey(), raw));

        JsonNode res = post("/v2/gateway/api/query", body);
        Long transId = res.hasNonNull("transId") ? res.get("transId").asLong() : null;
        return new MomoQueryResult(res.path("resultCode").asInt(-1), res.path("message").asText(""),
                res.path("amount").asLong(-1), transId, res.toString());
    }

    private JsonNode post(String path, Object body) {
        try {
            JsonNode node = restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            if (node == null) throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
            return node;
        } catch (RestClientException e) {
            log.error("MoMo call {} failed: {}", path, e.getMessage());
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    private void requireConfigured() {
        if (blank(props.partnerCode()) || blank(props.accessKey()) || blank(props.secretKey())) {
            log.error("MoMo is not configured: set MOMO_PARTNER_CODE, MOMO_ACCESS_KEY and MOMO_SECRET_KEY");
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
