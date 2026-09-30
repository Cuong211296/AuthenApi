# Clothing Shop - Plan 3: MoMo Payment, Expiry Job, Email, Backend Verification

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pay orders through MoMo (wallet, ATM card, international card on MoMo's page) with sandbox keys and no public tunnel, close unpaid orders safely, and email order confirmations.

**Architecture:** `MomoGateway` interface (real HTTP implementation + test fake) isolates MoMo. Payment finalization is one idempotent method (`PaymentFinalizer`) shared by the browser-return path and the IPN endpoint; the "pay once" guarantee is a conditional SQL update. A scheduled job first reconciles with MoMo (users who paid but never returned) and only then cancels overdue orders. Emails are sent asynchronously after commit from an `OrderConfirmedEvent`.

**Tech Stack:** Spring Boot 3.2.3, Spring 6.1 `RestClient`, Spring Mail, `@Async`, `@Scheduled`, JUnit 5 + H2.

**Spec:** `docs/superpowers/specs/2026-09-30-clothing-shop-design.md` (sections 5, 7, 8)

**Prerequisite:** Plans 1, 2a, 2b complete.

## Global Constraints

- MoMo API v2, sandbox base URL `https://test-payment.momo.vn`; signatures are HMAC-SHA256 hex over the `key=value&...` string in alphabetical key order, using `MOMO_SECRET_KEY`. Amounts are integer VND.
- No secrets in code or git: `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` come from `.env`. Blank keys must not stop the app from starting (COD keeps working); a MoMo call with blank keys fails with `PAYMENT_GATEWAY_ERROR`.
- Order payability window is `expiresAt` (15 minutes). A failed or cancelled MoMo attempt leaves the order `PENDING_PAYMENT` so the customer can retry until expiry.
- The browser return path never trusts MoMo's redirect parameters; it asks MoMo (`/v2/gateway/api/query`) for the truth using the stored `providerOrderId`/`requestId`.
- Commits end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- Return callback and IPN for the same payment, delivered twice or concurrently: order becomes paid once, exactly one confirmation event (Task 10 test).
- MoMo reports success with a different amount than the order total: order is NOT marked paid (Task 10 test).
- Payment succeeds after the order was already cancelled/expired: order stays cancelled and a loud warning is logged for manual refund (Task 10 test).
- Customer pays on MoMo then closes the tab before returning: the expiry job reconciles and confirms instead of cancelling (Task 11 test).
- MoMo unreachable during reconcile: the order is not cancelled (Task 11 test).
- Mail server down: order flow is unaffected, error only logged (Task 12 test); customer-supplied text is HTML-escaped in the email (Task 12 test).

(Paths: main under `src/main/java/com/example/identifyservice/`, tests under `src/test/java/com/example/identifyservice/`.)

---

### Task 10: MoMo signer, gateway, payment service and endpoints

**Files:**
- Create: `enums/PaymentAttemptStatus.java`, `entity/Payment.java`, `repository/PaymentRepository.java`, `momo/MomoProperties.java`, `momo/MomoSigner.java`, `momo/MomoGateway.java`, `momo/MomoCreateCommand.java`, `momo/MomoCreateResult.java`, `momo/MomoQueryResult.java`, `momo/MomoIpnRequest.java`, `momo/MomoHttpGateway.java`, `configuration/HttpClientConfig.java`, `service/FinalizeOutcome.java`, `service/PaymentFinalizer.java`, `service/PaymentService.java`, `dto/response/MomoPayResponse.java`, `dto/response/PaymentResultResponse.java`, `controller/PaymentController.java`
- Modify: `src/main/resources/application.yaml`, `src/test/resources/application-test.yaml`, `configuration/SecurityConfig.java`
- Test: `testsupport/FakeMomoGateway.java`, `testsupport/TestDataFactory.java` (extend), `momo/MomoSignerTest.java`, `momo/MomoHttpGatewayTest.java`, `service/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `OrderService.requireOwnedOrder`, `OrderRepository.markPaid`, `OrderConfirmedEvent`, `ErrorCode.*` (Plan 1).
- Produces:
  - `MomoGateway { MomoCreateResult create(MomoCreateCommand); MomoQueryResult query(String providerOrderId, String requestId); }`
  - `record MomoCreateCommand(String providerOrderId, String requestId, long amount, String orderInfo, String redirectUrl, String ipnUrl)`
  - `record MomoCreateResult(int resultCode, String message, String payUrl)`
  - `record MomoQueryResult(int resultCode, String message, long amount, Long transId, String raw)` (amount is `-1` when MoMo omits it)
  - `enum FinalizeOutcome { PAID, ALREADY_PROCESSED, LATE_PAYMENT_ORDER_CLOSED, PENDING, FAILED }`
  - `PaymentFinalizer.finalizePayment(String providerOrderId, int resultCode, long amount, Long transId, String raw) : FinalizeOutcome`
  - `PaymentService.startMomoPayment(String orderCode) : MomoPayResponse(String payUrl)`, `confirmMomoReturn(String orderCode) : PaymentResultResponse(String orderCode, OrderStatus orderStatus, PaymentStatus paymentStatus)`, `handleIpn(MomoIpnRequest)`, `reconcilePendingAttempts(Order order)`
  - Endpoints: `POST /orders/{code}/pay/momo` (auth), `GET /payments/momo/return?orderCode=` (auth), `POST /payments/momo/ipn` (public, signature-checked, replies 204)

- [ ] **Step 1: Add MoMo config.** Append to `application.yaml`:

```yaml
momo:
  endpoint: ${MOMO_ENDPOINT:https://test-payment.momo.vn}
  partner-code: ${MOMO_PARTNER_CODE:}
  access-key: ${MOMO_ACCESS_KEY:}
  secret-key: ${MOMO_SECRET_KEY:}
  request-type: ${MOMO_REQUEST_TYPE:payWithMethod}
  ipn-url: ${MOMO_IPN_URL:http://localhost:8081/identity/payments/momo/ipn}
```

Append to `src/test/resources/application-test.yaml`:

```yaml
momo:
  endpoint: https://test-payment.momo.vn
  partner-code: PARTNER
  access-key: ACCESS
  secret-key: SECRET
  request-type: payWithMethod
  ipn-url: http://localhost:8081/identity/payments/momo/ipn
```

Public-tunnel later: set `MOMO_IPN_URL` to the ngrok URL; no code change.

- [ ] **Step 2: Create `MomoProperties`, the gateway contract and records**

`momo/MomoProperties.java`:
```java
package com.example.identifyservice.momo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "momo")
public record MomoProperties(String endpoint, String partnerCode, String accessKey, String secretKey,
                             String requestType, String ipnUrl) {
}
```

`momo/MomoGateway.java`:
```java
package com.example.identifyservice.momo;

public interface MomoGateway {
    MomoCreateResult create(MomoCreateCommand command);

    MomoQueryResult query(String providerOrderId, String requestId);
}
```

`momo/MomoCreateCommand.java`:
```java
package com.example.identifyservice.momo;

public record MomoCreateCommand(String providerOrderId, String requestId, long amount, String orderInfo,
                                String redirectUrl, String ipnUrl) {
}
```

`momo/MomoCreateResult.java`:
```java
package com.example.identifyservice.momo;

public record MomoCreateResult(int resultCode, String message, String payUrl) {
}
```

`momo/MomoQueryResult.java`:
```java
package com.example.identifyservice.momo;

public record MomoQueryResult(int resultCode, String message, long amount, Long transId, String raw) {
}
```

`momo/MomoIpnRequest.java`:
```java
package com.example.identifyservice.momo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MomoIpnRequest(String partnerCode, String orderId, String requestId, long amount, String orderInfo,
                             String orderType, long transId, int resultCode, String message, String payType,
                             long responseTime, String extraData, String signature) {
}
```

- [ ] **Step 3: Write the failing signer test** `momo/MomoSignerTest.java`

```java
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
```

- [ ] **Step 4: Run to verify failure**

Run: `mvn -q test -Dtest=MomoSignerTest`
Expected: compilation error, `MomoSigner` missing.

- [ ] **Step 5: Implement `MomoSigner`**

```java
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
```

- [ ] **Step 6: Run to verify it passes**

Run: `mvn -q test -Dtest=MomoSignerTest`
Expected: 5 tests PASS.

- [ ] **Step 7: Write the failing HTTP gateway test** `momo/MomoHttpGatewayTest.java`

```java
package com.example.identifyservice.momo;

import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.HttpMethod;

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
```

- [ ] **Step 8: Implement `MomoHttpGateway` and the timeout customizer**

`momo/MomoHttpGateway.java`:
```java
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
```

The query response is fetched directly from MoMo over TLS, so its authenticity comes from the channel; only the IPN (which arrives from the internet) is signature-verified. If the sandbox docs later show a signature for query responses, add a check in `query`.

`configuration/HttpClientConfig.java` (timeouts so a hung MoMo cannot hang request threads):
```java
package com.example.identifyservice.configuration;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class HttpClientConfig {
    @Bean
    RestClientCustomizer httpTimeouts() {
        return builder -> {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5_000);
            factory.setReadTimeout(15_000);
            builder.requestFactory(factory);
        };
    }
}
```

- [ ] **Step 9: Run to verify the gateway tests pass**

Run: `mvn -q test -Dtest=MomoHttpGatewayTest`
Expected: 5 tests PASS.

- [ ] **Step 10: Create the payment entity, repository and enum**

`enums/PaymentAttemptStatus.java`:
```java
package com.example.identifyservice.enums;

public enum PaymentAttemptStatus {
    PENDING, SUCCESS, FAILED
}
```

`entity/Payment.java`:
```java
package com.example.identifyservice.entity;

import com.example.identifyservice.enums.PaymentAttemptStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    Order order;

    @Column(nullable = false, length = 20)
    @Builder.Default
    String provider = "MOMO";

    @Column(nullable = false, length = 64)
    String requestId;

    /** The orderId sent to MoMo for this attempt (unique per attempt, so retries never collide). */
    @Column(nullable = false, unique = true, length = 80)
    String providerOrderId;

    Long transId;

    long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    PaymentAttemptStatus status;

    @Column(length = 4000)
    String rawResponse;

    @CreationTimestamp
    @Column(updatable = false)
    Instant createdAt;
}
```

`repository/PaymentRepository.java`:
```java
package com.example.identifyservice.repository;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByProviderOrderId(String providerOrderId);
    List<Payment> findByOrderAndStatus(Order order, PaymentAttemptStatus status);
    List<Payment> findByOrder(Order order);
}
```

- [ ] **Step 11: Create test support: the fake gateway and factory extension**

`testsupport/FakeMomoGateway.java`:
```java
package com.example.identifyservice.testsupport;

import com.example.identifyservice.momo.MomoCreateCommand;
import com.example.identifyservice.momo.MomoCreateResult;
import com.example.identifyservice.momo.MomoGateway;
import com.example.identifyservice.momo.MomoQueryResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Replaces the real MoMo gateway in every Spring test. */
@Component
@Primary
public class FakeMomoGateway implements MomoGateway {
    public volatile MomoCreateResult createResult = new MomoCreateResult(0, "Successful.", "https://pay.example/checkout");
    public volatile Function<String, MomoQueryResult> queryHandler = id -> pending();
    public volatile RuntimeException queryFailure;
    public final List<MomoCreateCommand> creates = new CopyOnWriteArrayList<>();

    public static MomoQueryResult pending() {
        return new MomoQueryResult(1000, "pending", -1, null, "{}");
    }

    public static MomoQueryResult paid(long amount) {
        return new MomoQueryResult(0, "Successful.", amount, 555L, "{\"resultCode\":0}");
    }

    public void reset() {
        createResult = new MomoCreateResult(0, "Successful.", "https://pay.example/checkout");
        queryHandler = id -> pending();
        queryFailure = null;
        creates.clear();
    }

    @Override
    public MomoCreateResult create(MomoCreateCommand command) {
        creates.add(command);
        return createResult;
    }

    @Override
    public MomoQueryResult query(String providerOrderId, String requestId) {
        if (queryFailure != null) throw queryFailure;
        return queryHandler.apply(providerOrderId);
    }
}
```

Extend `testsupport/TestDataFactory.java`: add these imports (`Order`, `OrderItem`, `Payment`, `OrderRepository`, `PaymentRepository`, `OrderStatus`, `PaymentMethod`, `PaymentStatus`, `PaymentAttemptStatus`, `java.time.Instant`, `java.util.UUID`), these fields, and the methods:

```java
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;

    /** A MoMo order in PENDING_PAYMENT holding qty of the variant (stock is NOT decremented here). */
    public Order pendingMomoOrder(User user, ProductVariant variant, int qty, Instant expiresAt) {
        long unit = variant.effectivePrice();
        Order order = Order.builder().code("DHTEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .user(user).status(OrderStatus.PENDING_PAYMENT).paymentMethod(PaymentMethod.MOMO)
                .paymentStatus(PaymentStatus.UNPAID).receiverName("Test").phone("0901234567")
                .email("test@example.com").address("1 Test St").province("Hà Nội")
                .subtotal(unit * qty).shippingFee(25_000).total(unit * qty + 25_000).expiresAt(expiresAt).build();
        order.getItems().add(OrderItem.builder().order(order).variantId(variant.getId())
                .productName(variant.getProduct().getName()).size(variant.getSize()).color(variant.getColor())
                .unitPrice(unit).quantity(qty).build());
        return orders.save(order);
    }

    public Payment attempt(Order order, String providerOrderId) {
        return payments.save(Payment.builder().order(order).requestId(UUID.randomUUID().toString())
                .providerOrderId(providerOrderId).amount(order.getTotal())
                .status(PaymentAttemptStatus.PENDING).build());
    }
```

- [ ] **Step 12: Create the finalizer, outcome and result DTOs**

`service/FinalizeOutcome.java`:
```java
package com.example.identifyservice.service;

public enum FinalizeOutcome {
    PAID, ALREADY_PROCESSED, LATE_PAYMENT_ORDER_CLOSED, PENDING, FAILED
}
```

`dto/response/MomoPayResponse.java`:
```java
package com.example.identifyservice.dto.response;

public record MomoPayResponse(String payUrl) {
}
```

`dto/response/PaymentResultResponse.java`:
```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;

public record PaymentResultResponse(String orderCode, OrderStatus orderStatus, PaymentStatus paymentStatus) {
}
```

`service/PaymentFinalizer.java`:
```java
package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.PaymentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/** The single place where a MoMo result changes an order. Idempotent and safe under concurrent calls. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentFinalizer {
    /** MoMo result codes meaning "still in progress" (initiated, processing, authorized). */
    private static final Set<Integer> IN_PROGRESS = Set.of(1000, 7000, 7002, 9000);

    PaymentRepository paymentRepository;
    OrderRepository orderRepository;
    ApplicationEventPublisher publisher;

    @Transactional
    public FinalizeOutcome finalizePayment(String providerOrderId, int resultCode, long amount, Long transId,
                                           String raw) {
        Payment payment = paymentRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        Order order = payment.getOrder();

        if (IN_PROGRESS.contains(resultCode)) return FinalizeOutcome.PENDING;

        payment.setTransId(transId);
        payment.setRawResponse(raw == null ? null : raw.substring(0, Math.min(raw.length(), 4000)));

        if (resultCode != 0) {
            if (payment.getStatus() == PaymentAttemptStatus.PENDING) payment.setStatus(PaymentAttemptStatus.FAILED);
            paymentRepository.save(payment);
            return FinalizeOutcome.FAILED;
        }

        if (amount != payment.getAmount() || amount != order.getTotal())
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        payment.setStatus(PaymentAttemptStatus.SUCCESS);
        paymentRepository.save(payment);

        String orderId = order.getId();
        String orderCode = order.getCode();
        if (orderRepository.markPaid(orderId, Instant.now()) == 1) {
            publisher.publishEvent(new OrderConfirmedEvent(orderId));
            return FinalizeOutcome.PAID;
        }

        var current = orderRepository.findById(orderId).orElseThrow();
        if (current.getPaymentStatus() == PaymentStatus.PAID) return FinalizeOutcome.ALREADY_PROCESSED;
        log.warn("LATE PAYMENT: MoMo transId={} succeeded for order {} which is already {} - manual refund required",
                transId, orderCode, current.getStatus());
        return FinalizeOutcome.LATE_PAYMENT_ORDER_CLOSED;
    }
}
```

- [ ] **Step 13: Write the failing payment service test** `service/PaymentServiceTest.java`

```java
package com.example.identifyservice.service;

import com.example.identifyservice.configuration.ShopProperties;
import com.example.identifyservice.dto.response.PaymentResultResponse;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.momo.MomoIpnRequest;
import com.example.identifyservice.momo.MomoProperties;
import com.example.identifyservice.momo.MomoSigner;
import com.example.identifyservice.repository.PaymentRepository;
import com.example.identifyservice.testsupport.FakeMomoGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@WithMockUser(username = "alice", roles = "USER")
class PaymentServiceTest {
    @Autowired PaymentService payments;
    @Autowired TestDataFactory data;
    @Autowired FakeMomoGateway momo;
    @Autowired MomoProperties momoProps;
    @Autowired ShopProperties shopProps;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderService orders;
    @Autowired EntityManager em;
    @Autowired ApplicationEvents events;

    User alice;
    Order order;

    @BeforeEach
    void setUp() {
        momo.reset();
        alice = data.user("alice");
        var product = data.product("pay-tee", 100_000, true);
        ProductVariant variant = data.variant(product, "M", "red", 10, null);
        order = data.pendingMomoOrder(alice, variant, 2, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((AppException) t).getErrorCode();
    }

    private long confirmedEvents() {
        return events.stream(OrderConfirmedEvent.class).count();
    }

    @Test
    void startPaymentReturnsPayUrlAndRecordsAttemptWithRedirectToFrontend() {
        var response = payments.startMomoPayment(order.getCode());

        assertThat(response.payUrl()).isEqualTo("https://pay.example/checkout");
        assertThat(momo.creates).hasSize(1);
        var cmd = momo.creates.get(0);
        assertThat(cmd.amount()).isEqualTo(order.getTotal());
        assertThat(cmd.redirectUrl()).isEqualTo(shopProps.frontendUrl() + "/payment/result?orderCode=" + order.getCode());
        assertThat(cmd.ipnUrl()).isEqualTo(momoProps.ipnUrl());
        assertThat(paymentRepository.findByProviderOrderId(cmd.providerOrderId())).isPresent();
        assertThat(cmd.providerOrderId()).startsWith(order.getCode());
    }

    @Test
    void gatewayRefusalIsReportedAndNoAttemptIsStored() {
        momo.createResult = new com.example.identifyservice.momo.MomoCreateResult(99, "bad", "");
        assertThatThrownBy(() -> payments.startMomoPayment(order.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.PAYMENT_GATEWAY_ERROR);
        assertThat(paymentRepository.findByOrder(order)).isEmpty();
    }

    @Test
    void codOrExpiredOrOthersOrdersCannotBePaid() {
        Order cod = data.pendingMomoOrder(alice, data.variant(data.product("cod-tee", 1000, true), "S", "x", 1, null),
                1, null);
        cod.setPaymentMethod(PaymentMethod.COD);
        assertThatThrownBy(() -> payments.startMomoPayment(cod.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE);

        Order expired = data.pendingMomoOrder(alice, data.variant(data.product("old-tee", 1000, true), "S", "x", 1, null),
                1, Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThatThrownBy(() -> payments.startMomoPayment(expired.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE);

        data.user("bob");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", "x", "ROLE_USER"));
        assertThatThrownBy(() -> payments.startMomoPayment(order.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void returnMarksOrderPaidAndSecondReturnDoesNothingMore() {
        payments.startMomoPayment(order.getCode());
        momo.queryHandler = id -> FakeMomoGateway.paid(order.getTotal());

        PaymentResultResponse first = payments.confirmMomoReturn(order.getCode());
        PaymentResultResponse second = payments.confirmMomoReturn(order.getCode());

        assertThat(first.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(first.orderStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(second).isEqualTo(first);
        assertThat(confirmedEvents()).isEqualTo(1);
    }

    @Test
    void returnThenIpnForSamePaymentConfirmsOnce() {
        payments.startMomoPayment(order.getCode());
        String providerOrderId = momo.creates.get(0).providerOrderId();
        momo.queryHandler = id -> FakeMomoGateway.paid(order.getTotal());
        payments.confirmMomoReturn(order.getCode());

        payments.handleIpn(signedIpn(providerOrderId, order.getTotal(), 0));

        assertThat(confirmedEvents()).isEqualTo(1);
    }

    @Test
    void amountMismatchDoesNotMarkPaid() {
        payments.startMomoPayment(order.getCode());
        momo.queryHandler = id -> FakeMomoGateway.paid(1_000);

        assertThatThrownBy(() -> payments.confirmMomoReturn(order.getCode()))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        em.flush();
        em.clear();
        assertThat(orders.getMyOrder(order.getCode()).paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(confirmedEvents()).isZero();
    }

    @Test
    void stillPendingChangesNothing() {
        payments.startMomoPayment(order.getCode());
        var result = payments.confirmMomoReturn(order.getCode());
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void failedAttemptKeepsOrderPayableSoCustomerCanRetry() {
        payments.startMomoPayment(order.getCode());
        String firstAttempt = momo.creates.get(0).providerOrderId();
        momo.queryHandler = id -> new com.example.identifyservice.momo.MomoQueryResult(1006, "denied", -1, null, "{}");

        var result = payments.confirmMomoReturn(order.getCode());

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(paymentRepository.findByProviderOrderId(firstAttempt).orElseThrow().getStatus())
                .isEqualTo(PaymentAttemptStatus.FAILED);
        payments.startMomoPayment(order.getCode());
        assertThat(momo.creates).hasSize(2);
        assertThat(momo.creates.get(1).providerOrderId()).isNotEqualTo(firstAttempt);
    }

    @Test
    void paymentArrivingAfterCancellationDoesNotResurrectTheOrder() {
        payments.startMomoPayment(order.getCode());
        assertThat(orders.cancelPendingPayment(order.getId(), PaymentStatus.EXPIRED)).isTrue();
        momo.queryHandler = id -> FakeMomoGateway.paid(order.getTotal());

        var result = payments.confirmMomoReturn(order.getCode());

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(confirmedEvents()).isZero();
    }

    @Test
    void ipnWithBadSignatureIsRejectedAndValidOneIsAccepted() {
        payments.startMomoPayment(order.getCode());
        String providerOrderId = momo.creates.get(0).providerOrderId();

        MomoIpnRequest forged = new MomoIpnRequest("PARTNER", providerOrderId, "r", order.getTotal(), "i", "t", 1, 0,
                "ok", "qr", 1L, "", "deadbeef");
        assertThatThrownBy(() -> payments.handleIpn(forged))
                .isInstanceOf(AppException.class).extracting(e -> codeOf(e)).isEqualTo(ErrorCode.INVALID_PAYMENT_SIGNATURE);
        assertThat(confirmedEvents()).isZero();

        payments.handleIpn(signedIpn(providerOrderId, order.getTotal(), 0));
        assertThat(confirmedEvents()).isEqualTo(1);
    }

    private MomoIpnRequest signedIpn(String providerOrderId, long amount, int resultCode) {
        MomoIpnRequest unsigned = new MomoIpnRequest("PARTNER", providerOrderId, "req-1", amount, "info",
                "momo_wallet", 777, resultCode, "Successful.", "qr", 1700000000000L, "", null);
        String signature = MomoSigner.hmacSha256Hex(momoProps.secretKey(),
                MomoSigner.ipnRaw(momoProps.accessKey(), unsigned));
        return new MomoIpnRequest(unsigned.partnerCode(), unsigned.orderId(), unsigned.requestId(), unsigned.amount(),
                unsigned.orderInfo(), unsigned.orderType(), unsigned.transId(), unsigned.resultCode(),
                unsigned.message(), unsigned.payType(), unsigned.responseTime(), unsigned.extraData(), signature);
    }
}
```

Remove the unused `ApplicationEvent` import if the compiler warns.

- [ ] **Step 14: Run to verify failure**

Run: `mvn -q test -Dtest=PaymentServiceTest`
Expected: compilation error, `PaymentService` missing.

- [ ] **Step 15: Implement `PaymentService`**

```java
package com.example.identifyservice.service;

import com.example.identifyservice.configuration.ShopProperties;
import com.example.identifyservice.dto.response.MomoPayResponse;
import com.example.identifyservice.dto.response.PaymentResultResponse;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.Payment;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentAttemptStatus;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.momo.MomoCreateCommand;
import com.example.identifyservice.momo.MomoGateway;
import com.example.identifyservice.momo.MomoIpnRequest;
import com.example.identifyservice.momo.MomoProperties;
import com.example.identifyservice.momo.MomoQueryResult;
import com.example.identifyservice.momo.MomoSigner;
import com.example.identifyservice.repository.PaymentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Deliberately not @Transactional: it makes HTTP calls to MoMo and must not hold a DB transaction meanwhile. */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentService {
    OrderService orderService;
    PaymentRepository paymentRepository;
    PaymentFinalizer finalizer;
    MomoGateway gateway;
    MomoProperties momoProperties;
    ShopProperties shopProperties;

    public MomoPayResponse startMomoPayment(String orderCode) {
        Order order = orderService.requireOwnedOrder(orderCode);
        boolean payable = order.getPaymentMethod() == PaymentMethod.MOMO
                && order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getExpiresAt() != null && order.getExpiresAt().isAfter(Instant.now());
        if (!payable) throw new AppException(ErrorCode.ORDER_NOT_PAYABLE);

        String providerOrderId = order.getCode() + "_" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        var result = gateway.create(new MomoCreateCommand(providerOrderId, requestId, order.getTotal(),
                "Thanh toan don hang " + order.getCode(),
                shopProperties.frontendUrl() + "/payment/result?orderCode=" + order.getCode(),
                momoProperties.ipnUrl()));
        if (result.resultCode() != 0 || result.payUrl() == null || result.payUrl().isBlank())
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);

        paymentRepository.save(Payment.builder().order(order).requestId(requestId).providerOrderId(providerOrderId)
                .amount(order.getTotal()).status(PaymentAttemptStatus.PENDING).build());
        return new MomoPayResponse(result.payUrl());
    }

    /** Called when the customer's browser comes back from MoMo. MoMo's redirect parameters are ignored. */
    public PaymentResultResponse confirmMomoReturn(String orderCode) {
        Order order = orderService.requireOwnedOrder(orderCode);
        if (order.getPaymentMethod() != PaymentMethod.MOMO) throw new AppException(ErrorCode.ORDER_NOT_PAYABLE);
        if (order.getPaymentStatus() != PaymentStatus.PAID) reconcilePendingAttempts(order);
        Order fresh = orderService.requireOwnedOrder(orderCode);
        return new PaymentResultResponse(fresh.getCode(), fresh.getStatus(), fresh.getPaymentStatus());
    }

    /** Asks MoMo for the truth about every unresolved attempt of the order and applies it. */
    public void reconcilePendingAttempts(Order order) {
        for (Payment attempt : paymentRepository.findByOrderAndStatus(order, PaymentAttemptStatus.PENDING)) {
            MomoQueryResult q = gateway.query(attempt.getProviderOrderId(), attempt.getRequestId());
            finalizer.finalizePayment(attempt.getProviderOrderId(), q.resultCode(), q.amount(), q.transId(), q.raw());
        }
    }

    public void handleIpn(MomoIpnRequest ipn) {
        String expected = MomoSigner.hmacSha256Hex(momoProperties.secretKey(),
                MomoSigner.ipnRaw(momoProperties.accessKey(), ipn));
        if (!MomoSigner.constantTimeEquals(expected, ipn.signature()))
            throw new AppException(ErrorCode.INVALID_PAYMENT_SIGNATURE);
        finalizer.finalizePayment(ipn.orderId(), ipn.resultCode(), ipn.amount(), ipn.transId(), null);
    }
}
```

- [ ] **Step 16: Implement `PaymentController`**

```java
package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.response.MomoPayResponse;
import com.example.identifyservice.dto.response.PaymentResultResponse;
import com.example.identifyservice.momo.MomoIpnRequest;
import com.example.identifyservice.service.PaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {
    PaymentService paymentService;

    @PostMapping("/orders/{code}/pay/momo")
    ApiResponse<MomoPayResponse> payWithMomo(@PathVariable String code) {
        return ApiResponse.ok(paymentService.startMomoPayment(code));
    }

    @GetMapping("/payments/momo/return")
    ApiResponse<PaymentResultResponse> momoReturn(@RequestParam String orderCode) {
        return ApiResponse.ok(paymentService.confirmMomoReturn(orderCode));
    }

    @PostMapping("/payments/momo/ipn")
    ResponseEntity<Void> momoIpn(@RequestBody MomoIpnRequest request) {
        paymentService.handleIpn(request);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 17: Open the IPN route.** In `SecurityConfig`, add `"/payments/momo/ipn"` to `PUBLIC_POST_ENDPOINTS`.

- [ ] **Step 18: Run to verify it passes**

Run: `mvn -q test -Dtest=PaymentServiceTest,MomoSignerTest,MomoHttpGatewayTest`
Expected: all PASS (10 + 5 + 5).

- [ ] **Step 19: Commit**

```bash
git add src
git commit -m "feat: MoMo payment (signer, gateway, idempotent finalizer, return and IPN)"
```

---

### Task 11: Expiry job with MoMo reconciliation

**Files:**
- Create: `configuration/SchedulingConfig.java`, `service/OrderExpiryService.java`
- Test: `service/OrderExpiryServiceTest.java`

**Interfaces:**
- Consumes: `OrderRepository.findByStatusAndExpiresAtBefore`, `PaymentService.reconcilePendingAttempts`, `OrderService.cancelPendingPayment`.
- Produces: `OrderExpiryService.expireOverdueOrders() : int` (number of orders cancelled); runs every minute.

- [ ] **Step 1: Write the failing test** `service/OrderExpiryServiceTest.java` (not `@Transactional`: the job commits per order, like production)

```java
package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.FakeMomoGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrderExpiryServiceTest {
    @Autowired OrderExpiryService expiry;
    @Autowired TestDataFactory data;
    @Autowired FakeMomoGateway momo;
    @Autowired OrderRepository orders;
    @Autowired ProductVariantRepository variants;

    User user;
    ProductVariant variant;

    @BeforeEach
    void setUp() {
        momo.reset();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        user = data.user("exp-" + tag);
        variant = data.variant(data.product("exp-" + tag, 100_000, true), "M", "red", 3, null);
    }

    private Order overdueOrder(int qty) {
        // stock held by this order: simulate the reservation the checkout would have made
        variants.decrementStock(variant.getId(), qty);
        return data.pendingMomoOrder(user, variant, qty, Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    private Order reload(Order o) {
        return orders.findById(o.getId()).orElseThrow();
    }

    @Test
    void overdueUnpaidOrderIsCancelledAndStockReturned() {
        Order order = overdueOrder(2);
        data.attempt(order, order.getCode() + "_1");

        expiry.expireOverdueOrders();

        Order after = reload(order);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getPaymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(variants.findById(variant.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    @Test
    void notYetOverdueOrderIsLeftAlone() {
        variants.decrementStock(variant.getId(), 1);
        Order order = data.pendingMomoOrder(user, variant, 1, Instant.now().plus(10, ChronoUnit.MINUTES));
        expiry.expireOverdueOrders();
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void customerWhoPaidButNeverReturnedIsConfirmedNotCancelled() {
        Order order = overdueOrder(1);
        String providerOrderId = order.getCode() + "_1";
        data.attempt(order, providerOrderId);
        momo.queryHandler = id -> FakeMomoGateway.paid(order.getTotal());

        expiry.expireOverdueOrders();

        Order after = reload(order);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(after.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(variants.findById(variant.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void momoOutageDoesNotCancelAnOrderThatMightBePaid() {
        Order order = overdueOrder(1);
        data.attempt(order, order.getCode() + "_1");
        momo.queryFailure = new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);

        expiry.expireOverdueOrders();

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(variants.findById(variant.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void oneBrokenOrderDoesNotStopTheRest() {
        Order broken = overdueOrder(1);
        data.attempt(broken, broken.getCode() + "_1");
        Order fine = overdueOrder(1);
        momo.queryHandler = id -> {
            if (id.startsWith(broken.getCode())) throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR);
            return FakeMomoGateway.pending();
        };

        expiry.expireOverdueOrders();

        assertThat(reload(broken).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(reload(fine).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q test -Dtest=OrderExpiryServiceTest`
Expected: compilation error, `OrderExpiryService` missing.

- [ ] **Step 3: Implement the job**

`configuration/SchedulingConfig.java`:
```java
package com.example.identifyservice.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
```

`service/OrderExpiryService.java`:
```java
package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.enums.OrderStatus;
import com.example.identifyservice.enums.PaymentStatus;
import com.example.identifyservice.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Closes MoMo orders whose payment window elapsed. Because there is no public IPN in local development,
 * it first asks MoMo about every unresolved attempt, so a customer who paid and never returned is confirmed.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OrderExpiryService {
    OrderRepository orderRepository;
    PaymentService paymentService;
    OrderService orderService;

    @Scheduled(fixedDelayString = "${shop.expiry-job-interval-ms:60000}", initialDelayString = "60000")
    public void scheduledRun() {
        expireOverdueOrders();
    }

    /** @return number of orders cancelled in this run */
    public int expireOverdueOrders() {
        int cancelled = 0;
        for (Order order : orderRepository.findByStatusAndExpiresAtBefore(OrderStatus.PENDING_PAYMENT, Instant.now())) {
            try {
                paymentService.reconcilePendingAttempts(order);
                if (orderService.cancelPendingPayment(order.getId(), PaymentStatus.EXPIRED)) cancelled++;
            } catch (RuntimeException e) {
                log.warn("Could not expire order {} yet (will retry): {}", order.getCode(), e.getMessage());
            }
        }
        return cancelled;
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=OrderExpiryServiceTest`
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: expiry job reconciles with MoMo before cancelling overdue orders"
```

---

### Task 12: Order confirmation email

**Files:**
- Create: `service/OrderMailService.java`, `event/OrderMailListener.java`, `testsupport/RecordingMailSender.java`
- Modify: `repository/OrderRepository.java` (add `findWithItemsById`)
- Test: `service/OrderMailServiceTest.java`

**Interfaces:**
- Consumes: `OrderConfirmedEvent(String orderId)`, `ShopProperties.mailFrom`.
- Produces: `OrderMailService.sendOrderConfirmation(String orderId)` (throws on failure); `OrderMailListener.on(OrderConfirmedEvent)` (async, after commit, never throws).

- [ ] **Step 1: Add the eager-items lookup.** In `OrderRepository` add (with imports `org.springframework.data.jpa.repository.EntityGraph`):

```java
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(String id);
```

- [ ] **Step 2: Create the test mail sender** `testsupport/RecordingMailSender.java`

```java
package com.example.identifyservice.testsupport;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Replaces the SMTP sender in every Spring test; records messages instead of sending. */
@Component
@Primary
public class RecordingMailSender extends JavaMailSenderImpl {
    public final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    public volatile boolean failing;

    public void reset() {
        sent.clear();
        failing = false;
    }

    @Override
    public void send(MimeMessage mimeMessage) {
        if (failing) throw new MailSendException("SMTP down (test)");
        sent.add(mimeMessage);
    }
}
```

- [ ] **Step 3: Write the failing test** `service/OrderMailServiceTest.java`

```java
package com.example.identifyservice.service;

import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.event.OrderConfirmedEvent;
import com.example.identifyservice.event.OrderMailListener;
import com.example.identifyservice.repository.OrderRepository;
import com.example.identifyservice.testsupport.RecordingMailSender;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderMailServiceTest {
    @Autowired OrderMailService mailService;
    @Autowired RecordingMailSender sender;
    @Autowired TestDataFactory data;
    @Autowired OrderRepository orders;

    Order order;

    @BeforeEach
    void setUp() {
        sender.reset();
        var product = data.product("mail-tee", 150_000, true);
        ProductVariant v = data.variant(product, "M", "navy", 5, null);
        order = data.pendingMomoOrder(data.user("mailer"), v, 2, Instant.now());
        order.setReceiverName("<script>alert(1)</script> An");
        orders.save(order);
    }

    @Test
    void buildsVietnameseEmailWithOrderDetailsAndEscapesUserText() throws Exception {
        mailService.sendOrderConfirmation(order.getId());

        assertThat(sender.sent).hasSize(1);
        MimeMessage message = sender.sent.get(0);
        assertThat(message.getSubject()).contains(order.getCode());
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("test@example.com");
        String body = message.getContent().toString();
        assertThat(body).contains("Product mail-tee").contains("M").contains("navy")
                .contains("325.000")           // 2 x 150.000 + 25.000 shipping
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void sendFailurePropagatesFromServiceButListenerSwallowsIt() {
        sender.failing = true;
        assertThatThrownBy(() -> mailService.sendOrderConfirmation(order.getId())).isInstanceOf(RuntimeException.class);

        OrderMailService broken = mock(OrderMailService.class);
        doThrow(new RuntimeException("boom")).when(broken).sendOrderConfirmation("x");
        assertThatCode(() -> new OrderMailListener(broken).on(new OrderConfirmedEvent("x"))).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 4: Run to verify failure**

Run: `mvn -q test -Dtest=OrderMailServiceTest`
Expected: compilation error, `OrderMailService`/`OrderMailListener` missing.

- [ ] **Step 5: Implement the mail service and listener**

`service/OrderMailService.java`:
```java
package com.example.identifyservice.service;

import com.example.identifyservice.configuration.ShopProperties;
import com.example.identifyservice.entity.Order;
import com.example.identifyservice.entity.OrderItem;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.OrderRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderMailService {
    JavaMailSender mailSender;
    OrderRepository orderRepository;
    ShopProperties shopProperties;

    @Transactional(readOnly = true)
    public void sendOrderConfirmation(String orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(shopProperties.mailFrom());
            helper.setTo(order.getEmail());
            helper.setSubject("Xác nhận đơn hàng " + order.getCode());
            helper.setText(buildHtml(order), true);
            mailSender.send(message);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Cannot build confirmation email", e);
        }
    }

    private String buildHtml(Order o) {
        StringBuilder rows = new StringBuilder();
        for (OrderItem i : o.getItems()) {
            rows.append("<tr><td>").append(h(i.getProductName())).append("</td><td>").append(h(i.getSize()))
                    .append(" / ").append(h(i.getColor())).append("</td><td>").append(i.getQuantity())
                    .append("</td><td>").append(vnd(i.getUnitPrice() * i.getQuantity())).append("</td></tr>");
        }
        String payment = o.getPaymentMethod() == PaymentMethod.MOMO
                ? "Đã thanh toán qua MoMo" : "Thanh toán khi nhận hàng (COD)";
        return "<h2>Cảm ơn bạn đã đặt hàng!</h2>"
                + "<p>Xin chào " + h(o.getReceiverName()) + ", đơn hàng <b>" + h(o.getCode()) + "</b> đã được ghi nhận.</p>"
                + "<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\"><tr><th>Sản phẩm</th><th>Size / Màu</th>"
                + "<th>SL</th><th>Thành tiền</th></tr>" + rows + "</table>"
                + "<p>Tạm tính: " + vnd(o.getSubtotal()) + "<br>Phí vận chuyển: " + vnd(o.getShippingFee())
                + "<br><b>Tổng cộng: " + vnd(o.getTotal()) + "</b></p>"
                + "<p>" + payment + "</p>"
                + "<p>Giao đến: " + h(o.getAddress()) + ", " + h(o.getProvince()) + " - SĐT " + h(o.getPhone()) + "</p>";
    }

    private static String h(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }

    private static String vnd(long amount) {
        return NumberFormat.getInstance(new Locale("vi", "VN")).format(amount) + " ₫";
    }
}
```

`event/OrderMailListener.java`:
```java
package com.example.identifyservice.event;

import com.example.identifyservice.service.OrderMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the confirmation email after the order transaction commits; a mail failure never affects the order. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMailListener {
    private final OrderMailService mailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(OrderConfirmedEvent event) {
        try {
            mailService.sendOrderConfirmation(event.orderId());
        } catch (Exception e) {
            log.error("Could not send confirmation email for order {}: {}", event.orderId(), e.getMessage());
        }
    }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `mvn -q test -Dtest=OrderMailServiceTest`
Expected: 2 tests PASS.

- [ ] **Step 7: Run the whole suite and commit**

Run: `mvn -q test`
Expected: all PASS.

```bash
git add src
git commit -m "feat: async order confirmation email"
```

---

### Task 13: Real-database verification, docs, spec amendments

**Files:**
- Modify: `CLAUDE.md`, `docs/superpowers/specs/2026-09-30-clothing-shop-design.md`, `.env` (local, not committed)

- [ ] **Step 1: Back up the real database** (CLAUDE.md database workflow)

Run: `python scripts/dbtool.py backup`
Expected: a new file appears in `backups/` (git-ignored).

- [ ] **Step 2: Inspect the existing schema before touching it**

Use the `identity-db` MCP tools (`get_schema_info`) or `python scripts/dbtool.py`. Confirm the `user` table exists and check whether `role` already contains rows named `USER`/`ADMIN`.

- [ ] **Step 3: Apply the role backfill**

Run: `python scripts/dbtool.py run migration_v3_roles_backfill.sql`
Expected: success. Re-inspect: every non-admin user in `user_role` has a row.

- [ ] **Step 4: Start against MySQL and check for DDL errors**

Make sure `.env` has `DB_PASSWORD`, `JWT_SIGNER_KEY`, `ADMIN_PASSWORD`. Run from the project root:

Run: `mvn spring-boot:run` (leave running; stop with Ctrl+C when done)
Expected in the log: `Started IdentifyServiceApplication`, and **no** `Error executing DDL`. Then confirm new tables exist: `category`, `product`, `product_variant`, `cart`, `cart_item`, `orders`, `order_item`, `payment`, `shipping_rate`, and the new `user.locked_until` column. If a DDL error appears, read it: an existing-column type conflict needs a hand-written migration, not a guess.

- [ ] **Step 5: Exercise the API by hand** (second terminal)

```bash
BASE=http://localhost:8081/identity
ADMIN_PW='<the ADMIN_PASSWORD from .env>'
TOKEN=$(curl -s $BASE/auth/token -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PW\"}" | node -e "process.stdin.on('data',d=>console.log(JSON.parse(d).result.token))")
curl -s $BASE/shipping/fee?province=H%C3%A0%20N%E1%BB%99i          # public, expect fee 25000
curl -s $BASE/admin/products -H "Authorization: Bearer $TOKEN"     # expect code 1000
curl -s -o /dev/null -w '%{http_code}\n' $BASE/admin/products      # expect 401
```
Expected: the three results above. Signup a customer (`POST /users` with username, password 8+ chars, firstname, lastname, dob at least 10 years ago), log in, and confirm the response of `GET /users/myInfo` lists role `USER`.

- [ ] **Step 6: MoMo sandbox and mail credentials.** Ask the user for: MoMo sandbox `partnerCode`, `accessKey`, `secretKey` (from the MoMo developer portal / documentation; MoMo publishes public test credentials and test wallet/cards there) and either Mailtrap or Gmail app-password SMTP settings. Add them to `.env` as `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`. Restart the app.

- [ ] **Step 7: Manual MoMo round trip via API** (the UI comes in the frontend plan)

Create a category/product/variant as admin, add it to a customer's cart, `POST /orders` with `"paymentMethod":"MOMO"`, then `POST /orders/{code}/pay/momo`. Expected: a `payUrl` on the MoMo sandbox domain. Open it, pay with the sandbox test wallet/card from MoMo's docs, then call `GET /payments/momo/return?orderCode=<code>` as the customer. Expected: `paymentStatus` `PAID`, `orderStatus` `PENDING_CONFIRM`, and a confirmation email in the mail inbox. If MoMo rejects `requestType` `payWithMethod`, set `MOMO_REQUEST_TYPE=captureWallet` in `.env` and retry.

- [ ] **Step 8: Record deviations in the spec.** Append to `docs/superpowers/specs/2026-09-30-clothing-shop-design.md`:

```markdown
## 11. Amendments made during planning (2026-09-30)

- Shipping table is seeded with the 34 provincial-level units in force since July 2025 (not 63); admins can add rows.
- `Order` also stores the customer `email` captured at checkout (used for the confirmation email).
- A failed or cancelled MoMo attempt leaves the order `PENDING_PAYMENT` so the customer can retry until `expiresAt`; the expiry job cancels it afterwards (replaces "a failed result cancels immediately").
- `GET /payments/momo/return?orderCode=` ignores MoMo's redirect parameters and queries MoMo instead.
- The expiry job reconciles with MoMo before cancelling, because without a public IPN a customer who paid and closed the tab would otherwise be cancelled.
- Signup also fixed: client-supplied `id` no longer overwrites existing users; new users get role USER; accounts lock for 15 minutes after 5 failed logins.
- Frontend refreshes the JWT proactively before expiry (the backend refresh endpoint rejects expired tokens), rather than on 401.
```

- [ ] **Step 9: Update `CLAUDE.md`.** Add a short "Shop" section: new packages (`momo/`, `event/`, `util/`), the endpoint list from the spec, the `.env` variables (`ADMIN_PASSWORD`, `MOMO_*`, `MAIL_*`, `FRONTEND_URL`), that tests run on H2 (`mvn test` needs no MySQL), and that the app now refuses to start without `JWT_SIGNER_KEY`, `DB_PASSWORD`, `ADMIN_PASSWORD`.

- [ ] **Step 10: Final full test run and commit**

Run: `mvn -q test`
Expected: all PASS.

```bash
git add CLAUDE.md docs
git commit -m "docs: shop backend notes and spec amendments"
```
