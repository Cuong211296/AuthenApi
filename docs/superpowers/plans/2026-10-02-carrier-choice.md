# Carrier choice and carrier switches Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At checkout the customer sees every carrier that can quote their address (GHN, GHTK), the cheaper one pre-selected, and can pick another; the admin can switch each carrier on or off from the shop settings page.

**Architecture:** `ShippingQuoteService` is split into "ask each available carrier" (GHN on the caller thread, GHTK on a small daemon pool, in parallel) and "pick" (cheapest, GHN on a tie, else the fixed province table). `POST /shipping/quote` keeps its fields and gains `options`; `POST /orders` gains an optional `carrier` and re-quotes that carrier server-side. Carrier switches are two boolean columns on the single `shop_settings` row, cached in `ShopSettingsService`'s in-memory snapshot, changed through `PUT /admin/settings/carriers`.

**Tech Stack:** Spring Boot 3.2.3 / Java 17 / JPA (H2 in tests, MySQL in prod), JUnit 5 + AssertJ + MockMvc; React 18 + Vite + Vitest (shop-ui), Playwright for the live smoke test.

**Spec:** `docs/superpowers/specs/2026-10-02-carrier-choice-design.md`

## Global Constraints

- Quote fee, weight and value are computed server-side from database data, never from the client (spec: Rules, API).
- `GET /shipping/quote` response keeps the existing top-level fields (`fee, source, estimated, weightGrams, deliverable, message`), which describe the default selection; `options` is additive.
- `options` never contains `TABLE`; it is `[]` when the fee is the table fee; cheapest first, GHN first on a tie.
- A carrier is available only when configured in `.env` AND switched on. Both unavailable -> fixed province table (`source: TABLE`, `estimated: true`).
- A chosen carrier that no longer quotes -> `SHIPPING_CARRIER_UNAVAILABLE`, HTTP 409, code 2024; never silently switch carrier.
- The switches control fee quoting only. GHN address master data and `addressMode` stay tied to GHN credentials.
- Switches default to ON (new columns `BOOLEAN NOT NULL DEFAULT TRUE`); a change clears the quote cache and applies to the next quote immediately.
- `PUT /admin/settings/carriers` is ADMIN-only (already covered by the `/admin/**` rule) and separate from `PUT /admin/settings/shop`.
- No unit changes to `ShippingQuoteService` public constructors (tests build it directly with 8 or 9 arguments).
- Existing DB: `migration_v5_carrier_toggle.sql`, run after a backup (`python scripts/dbtool.py backup`, then `python scripts/dbtool.py run migration_v5_carrier_toggle.sql`) before starting the new build.
- Tests run on H2: `mvn -q test -Dtest=<Class>`; frontend: `cd shop-ui && npx vitest run <file>`. Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- Both carriers return the same fee: the default must be GHN, deterministically (Task 2 test `equalFeesSelectGhn`).
- The customer's chosen carrier stops quoting (down, or switched off) between the quote and the order: 409, no order, stock and cart untouched (Task 3 test `chosenCarrierThatStoppedQuotingIsRejectedAndNothingChanges`).
- `carrier` is garbage or `TABLE`: 400 `INVALID_INPUT` instead of a 500 (Task 3 test `unknownOrTableCarrierIsInvalidInput`).
- A carrier switched ON but without `.env` credentials must never be asked for a fee; both OFF must use the table (Task 2 tests `unconfiguredCarrierIsNeverQueriedEvenWhenSwitchedOn`, `bothSwitchedOffUsesTheTable`).
- A slow carrier must not double the wait: both are asked in parallel (Task 2 test `carriersAreQuotedInParallel`).
- The admin toggles a switch while a save is in flight, or the save fails: the switch must revert and show an error (Task 5, checked live in Task 6).

## File Structure

Backend (`src/main/java/com/example/identifyservice/`):
- Create `service/CarrierSwitches.java`: immutable `(ghn, ghtk)` flags, `ALL_ON`.
- Create `dto/request/CarrierSwitchesRequest.java`, `controller/AdminCarrierSettingsController.java`.
- Modify `entity/ShopSettings.java` (two columns), `dto/response/ShopSettingsResponse.java` (`configured` + `enabled`), `service/ShopSettingsService.java` (snapshot, `carrierSwitches()`, `updateCarriers`).
- Modify `service/ShippingQuoteService.java` (availability from switches, `QuoteOptions`, `quoteOptionsResolved`, `quoteForCarrier`, parallel GHTK), `exception/ErrorCode.java` (2024).
- Modify `dto/response/ShippingQuoteResponse.java`, `controller/ShippingController.java`, `dto/request/CheckoutRequest.java`, `service/OrderService.java`.
- Create `migration_v5_carrier_toggle.sql` (repo root, next to `migration_v4_shipping.sql`).

Frontend (`shop-ui/src/`):
- Modify `utils/shipping.js` (+ test): option helpers. Create `components/CarrierChoice.jsx`. Modify `pages/Checkout.jsx`, `pages/Checkout.css`.
- Modify `utils/settings.js` (+ test), `pages/admin/AdminSettings.jsx`, `pages/admin/Admin.css`.

Tests (`src/test/java/com/example/identifyservice/`): new `service/ShippingQuoteOptionsTest.java`, `service/OrderCheckoutCarrierTest.java`; additions to `ShopSettingsServiceTest`, `ShopSettingsControllerTest`, `ShippingQuoteControllerTest`, `CheckoutShippingTest`; one assertion fix in `ShippingQuoteGhnTest`.

---

### Task 1: Carrier switches (backend storage and admin API)

**Files:**
- Create: `src/main/java/com/example/identifyservice/service/CarrierSwitches.java`
- Create: `src/main/java/com/example/identifyservice/dto/request/CarrierSwitchesRequest.java`
- Create: `src/main/java/com/example/identifyservice/controller/AdminCarrierSettingsController.java`
- Create: `migration_v5_carrier_toggle.sql`
- Modify: `entity/ShopSettings.java`, `dto/response/ShopSettingsResponse.java`, `service/ShopSettingsService.java`
- Test: `src/test/java/.../service/ShopSettingsServiceTest.java`, `src/test/java/.../controller/ShopSettingsControllerTest.java`

**Interfaces:**
- Produces: `record CarrierSwitches(boolean ghn, boolean ghtk)` with `static final CarrierSwitches ALL_ON`; `ShopSettingsService.carrierSwitches(): CarrierSwitches` (in-memory snapshot, never null); `ShopSettingsService.updateCarriers(CarrierSwitchesRequest, String username): ShopSettingsResponse`; `ShopSettingsResponse.Ghn(boolean configured, boolean enabled, String shopId)` and `ShopSettingsResponse.Ghtk(boolean configured, boolean enabled)` (`configured` = credentials in `.env`, `enabled` = the switch).

- [ ] **Step 1: Write the failing tests**

Add to `ShopSettingsServiceTest` (imports: `com.example.identifyservice.dto.request.CarrierSwitchesRequest`; `java.util.concurrent.atomic.AtomicInteger`):

```java
    @Test
    void carrierSwitchesDefaultToOnPersistAndSurviveAReload() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        assertThat(s.carrierSwitches()).isEqualTo(CarrierSwitches.ALL_ON);

        ShopSettingsResponse r = s.updateCarriers(new CarrierSwitchesRequest(false, true), "boss");

        assertThat(r.carriers().ghn().configured()).isTrue();
        assertThat(r.carriers().ghn().enabled()).isFalse();
        assertThat(r.carriers().ghtk().enabled()).isTrue();
        assertThat(r.updatedBy()).isEqualTo("boss");
        assertThat(s.carrierSwitches()).isEqualTo(new CarrierSwitches(false, true));
        // a fresh service has an empty snapshot and must read the stored row
        assertThat(service(GHN_ON, GHTK_ON).carrierSwitches()).isEqualTo(new CarrierSwitches(false, true));
    }

    @Test
    void changingTheSwitchesNotifiesListeners() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        AtomicInteger calls = new AtomicInteger();
        s.addChangeListener(calls::incrementAndGet);
        s.updateCarriers(new CarrierSwitchesRequest(true, false), "boss");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void savingTheShopFormKeepsTheSwitches() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        s.updateCarriers(new CarrierSwitchesRequest(false, true), "boss");
        s.update(req("Quini Bear", "0901234567", hcmIds()), "boss");
        assertThat(s.carrierSwitches()).isEqualTo(new CarrierSwitches(false, true));
        assertThat(service(GHN_ON, GHTK_ON).carrierSwitches()).isEqualTo(new CarrierSwitches(false, true));
    }

    @Test
    void shopFormSavedFirstLeavesTheSwitchesOn() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        s.update(req("Quini Bear", "0901234567", hcmIds()), "boss");   // creates the row without touching the switches
        assertThat(service(GHN_ON, GHTK_ON).carrierSwitches()).isEqualTo(CarrierSwitches.ALL_ON);
    }

    @Test
    void missingSwitchValueIsInvalidInput() {
        ShopSettingsService s = service(GHN_ON, GHTK_ON);
        assertInvalid(() -> s.updateCarriers(new CarrierSwitchesRequest(null, true), "boss"));
        assertInvalid(() -> s.updateCarriers(null, "boss"));
    }
```

In the same file, the existing assertions that mean "credentials present" must use `configured()`. Replace: line ~102 `r.carriers().ghn().enabled()` -> `r.carriers().ghn().configured()`; line ~104 `r.carriers().ghtk().enabled()` -> `.configured()`; line ~150 `r.carriers().ghn().enabled()` -> `.configured()`; line ~219 `s.get().carriers().ghtk().enabled()` -> `.configured()`.

Add to `ShopSettingsControllerTest` (add `static final String CARRIERS = "/admin/settings/carriers";`):

```java
    @Test
    void carrierSwitchesAreAdminOnly() throws Exception {
        String body = "{\"ghn\":false,\"ghtk\":true}";
        mvc.perform(put(CARRIERS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        var user = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
        mvc.perform(put(CARRIERS).with(user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTurnsACarrierOffAndGetShowsIt() throws Exception {
        mvc.perform(put(CARRIERS).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ghn\":false,\"ghtk\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.carriers.ghn.enabled").value(false))
                .andExpect(jsonPath("$.result.carriers.ghtk.enabled").value(true))
                .andExpect(jsonPath("$.result.updatedBy").value("boss"));
        mvc.perform(get(URL).with(admin()))
                .andExpect(jsonPath("$.result.carriers.ghn.enabled").value(false))
                .andExpect(jsonPath("$.result.carriers.ghn.configured").value(true));
    }

    @Test
    void aMissingSwitchIsInvalidInput() throws Exception {
        mvc.perform(put(CARRIERS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"ghn\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
    }
```

- [ ] **Step 2: Run to verify the tests fail**

Run: `mvn -q test -Dtest='ShopSettingsServiceTest,ShopSettingsControllerTest'`
Expected: COMPILATION ERROR (`CarrierSwitchesRequest`, `CarrierSwitches`, `configured()` do not exist).

- [ ] **Step 3: Implement**

`service/CarrierSwitches.java`:

```java
package com.example.identifyservice.service;

/** Admin on/off switches of the carriers. A carrier quotes only when it is configured in .env AND switched on. */
public record CarrierSwitches(boolean ghn, boolean ghtk) {
    public static final CarrierSwitches ALL_ON = new CarrierSwitches(true, true);
}
```

`dto/request/CarrierSwitchesRequest.java`:

```java
package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.NotNull;

/** Both switches are always sent, so a partial body can never silently flip the other carrier. */
public record CarrierSwitchesRequest(
        @NotNull(message = "INVALID_INPUT") Boolean ghn,
        @NotNull(message = "INVALID_INPUT") Boolean ghtk) {
}
```

`controller/AdminCarrierSettingsController.java`:

```java
package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.CarrierSwitchesRequest;
import com.example.identifyservice.dto.response.ShopSettingsResponse;
import com.example.identifyservice.service.ShopSettingsService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings/carriers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminCarrierSettingsController {
    ShopSettingsService shopSettingsService;

    @PutMapping
    ApiResponse<ShopSettingsResponse> update(@RequestBody @Valid CarrierSwitchesRequest request,
                                             Authentication authentication) {
        return ApiResponse.ok(shopSettingsService.updateCarriers(request, authentication.getName()));
    }
}
```

`entity/ShopSettings.java`: add `import lombok.Builder;` is already there; add the two fields before `Instant updatedAt;`:

```java
    /** Carrier quoting switches (default on). A carrier also needs its .env credentials to be used. */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean not null default true")
    boolean ghnEnabled = true;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean not null default true")
    boolean ghtkEnabled = true;
```

Also change the class comment "Every column is nullable (no settings yet)" to "Every column except the two carrier switches is nullable (no settings yet)".

`dto/response/ShopSettingsResponse.java`: replace the two nested records:

```java
    /** {@code configured}: credentials present in .env. {@code enabled}: the admin switch (default on). */
    public record Ghn(boolean configured, boolean enabled, String shopId) {
    }

    public record Ghtk(boolean configured, boolean enabled) {
    }
```

`service/ShopSettingsService.java`:
1. Add field `private final AtomicReference<CarrierSwitches> switches = new AtomicReference<>();` next to `snapshot`.
2. Add after `pickup()`:

```java
    /** The carrier switches for the quote path: an in-memory snapshot, all on when nothing was saved. */
    public CarrierSwitches carrierSwitches() {
        CarrierSwitches current = switches.get();
        if (current != null) return current;
        CarrierSwitches loaded = repository.findById(ID).map(ShopSettingsService::toSwitches)
                .orElse(CarrierSwitches.ALL_ON);
        switches.compareAndSet(null, loaded);   // an update that raced ahead of this load wins
        return switches.get();
    }
```
3. `invalidateSnapshot()` also does `switches.set(null);`.
4. Add after `update(...)`:

```java
    /** Switches carriers on or off. Database only (no HTTP), applies to the next quote through the change listeners. */
    @PreAuthorize("hasRole('ADMIN')")
    public ShopSettingsResponse updateCarriers(CarrierSwitchesRequest request, String username) {
        if (request == null || request.ghn() == null || request.ghtk() == null) throw invalid();
        synchronized (writeLock) {
            ShopSettings row = repository.findById(ID).orElseGet(() -> ShopSettings.builder().id(ID).build());
            row.setGhnEnabled(request.ghn());
            row.setGhtkEnabled(request.ghtk());
            row.setUpdatedAt(clock.instant());
            row.setUpdatedBy(username);
            ShopSettings saved = repository.save(row);
            switches.set(toSwitches(saved));
            changeListeners.forEach(Runnable::run);
            return toResponse(saved);
        }
    }
```
   and the import `com.example.identifyservice.dto.request.CarrierSwitchesRequest`.
5. In `toResponse`, replace the `carriers` construction:

```java
        CarrierSwitches sw = row == null ? CarrierSwitches.ALL_ON : toSwitches(row);
        var carriers = new ShopSettingsResponse.Carriers(
                new ShopSettingsResponse.Ghn(ghnProps.isEnabled(), sw.ghn(), shopId),
                new ShopSettingsResponse.Ghtk(ghtkProps.isEnabled(pickup.provinceName(), pickup.wardName()), sw.ghtk()));
```
6. Add next to `toPickup`:

```java
    private static CarrierSwitches toSwitches(ShopSettings s) {
        return new CarrierSwitches(s.isGhnEnabled(), s.isGhtkEnabled());
    }
```

`migration_v5_carrier_toggle.sql` (repo root):

```sql
-- Carrier on/off switches (v5). Plain MySQL, safe to run more than once. Take a backup first:
--   python scripts/dbtool.py backup
--   python scripts/dbtool.py run migration_v5_carrier_toggle.sql
--
-- shop_settings.ghn_enabled / ghtk_enabled: BOOLEAN NOT NULL DEFAULT TRUE (every carrier stays on until the admin
-- turns it off). The application also adds the columns on startup, so add only what is missing, then backfill and
-- tighten (a column Hibernate created first may be nullable).
SET @add_ghn = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE shop_settings ADD COLUMN ghn_enabled BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_settings' AND COLUMN_NAME = 'ghn_enabled');
PREPARE add_ghn_stmt FROM @add_ghn;
EXECUTE add_ghn_stmt;
DEALLOCATE PREPARE add_ghn_stmt;

SET @add_ghtk = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE shop_settings ADD COLUMN ghtk_enabled BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_settings' AND COLUMN_NAME = 'ghtk_enabled');
PREPARE add_ghtk_stmt FROM @add_ghtk;
EXECUTE add_ghtk_stmt;
DEALLOCATE PREPARE add_ghtk_stmt;

UPDATE shop_settings SET ghn_enabled = TRUE WHERE ghn_enabled IS NULL;
UPDATE shop_settings SET ghtk_enabled = TRUE WHERE ghtk_enabled IS NULL;
ALTER TABLE shop_settings MODIFY ghn_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE shop_settings MODIFY ghtk_enabled BOOLEAN NOT NULL DEFAULT TRUE;
```

- [ ] **Step 4: Run to verify the tests pass**

Run: `mvn -q test -Dtest='ShopSettingsServiceTest,ShopSettingsControllerTest'`
Expected: PASS. If another test class fails to compile because of `Ghn(...)`/`Ghtk(...)` arity, fix it to the new `(configured, enabled, ...)` shape.

- [ ] **Step 5: Commit**

```bash
git add migration_v5_carrier_toggle.sql src/main src/test
git commit -m "feat: admin on/off switches for carriers (shop_settings, PUT /admin/settings/carriers)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Quote options, cheapest default, carrier selection (ShippingQuoteService)

**Files:**
- Modify: `src/main/java/com/example/identifyservice/service/ShippingQuoteService.java`
- Modify: `src/main/java/com/example/identifyservice/exception/ErrorCode.java`
- Create: `src/test/java/com/example/identifyservice/service/ShippingQuoteOptionsTest.java`
- Modify: `src/test/java/com/example/identifyservice/service/ShippingQuoteGhnTest.java:117`

**Interfaces:**
- Consumes: `CarrierSwitches`, `ShopSettingsService.carrierSwitches()` (Task 1); existing `ghnQuote`, `tableQuote`, `unsupportedQuote`, `cacheGet/cachePut`, breakers.
- Produces: `record QuoteOptions(ShippingQuote selected, List<ShippingQuote> options)` nested in `ShippingQuoteService`; `QuoteOptions quoteOptionsResolved(CartMeasure, QuoteAddress)`; `QuoteOptions quoteOptionsForCurrentCart(QuoteAddress)`; `ShippingQuote quoteForCarrier(CartMeasure, QuoteAddress, ShippingSource carrier)` (null carrier = selected; unavailable -> `AppException(SHIPPING_CARRIER_UNAVAILABLE)`); `quoteResolved` now returns `quoteOptionsResolved(...).selected()`; `ErrorCode.SHIPPING_CARRIER_UNAVAILABLE` (2024, 409).

- [ ] **Step 1: Write the failing tests**

`ShippingQuoteOptionsTest.java`:

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CarrierSwitchesRequest;
import com.example.identifyservice.dto.response.ShippingQuote;
import com.example.identifyservice.entity.Cart;
import com.example.identifyservice.entity.CartItem;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghn.GhnFeeResult;
import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.ghn.GhnProperties;
import com.example.identifyservice.ghtk.GhtkFeeResult;
import com.example.identifyservice.ghtk.GhtkProperties;
import com.example.identifyservice.repository.ShopSettingsRepository;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.MutableClock;
import com.example.identifyservice.testsupport.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShippingQuoteOptionsTest {
    static final GhnProperties GHN_ON = new GhnProperties("GT", "1", "https://ghn.test", null, 2, 25, 20, 10);
    static final GhtkProperties GHTK_ON = new GhtkProperties("TEST-TOKEN", "TESTSRC", "https://ghtk.test",
            "Hà Nội", "Phường Test", null, null, "road");
    static final GhtkProperties GHTK_OFF = new GhtkProperties("", "", "https://ghtk.test", "", "", null, null, "road");
    // ids of the fake sample tree: TP HCM (202) > Quận 1 (1442) > Phường Bến Nghé (20308)
    static final QuoteAddress HCM_IDS = new QuoteAddress("x", "x", "x", "12 Nguyen Hue", 202, 1442, "20308");

    @Autowired FakeGhnGateway ghn;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingService shipping;
    @Autowired CartMeasurer measurer;
    @Autowired TestDataFactory data;
    @Autowired ShopSettingsRepository repository;

    MutableClock clock;
    ShopSettingsService settings;
    ShippingQuoteService service;
    ProductVariant tee;

    @BeforeEach
    void setUp() {
        ghn.reset();
        ghtk.reset();
        ghn.useSampleData();
        repository.deleteAll();
        clock = new MutableClock(Instant.parse("2026-10-02T00:00:00Z"));
        build(GHN_ON, GHTK_ON);
        tee = data.variant(data.product("opt-tee", 200_000, true), "M", "white", 50, null);
        ghn.returnFee(38_500);
        ghtk.returnFee(32_000);
    }

    @AfterEach
    void tearDown() {
        ghn.reset();
        ghtk.reset();
        repository.deleteAll();
    }

    private void build(GhnProperties g, GhtkProperties k) {
        GhnMasterDataService md = new GhnMasterDataService(ghn, g, clock);
        settings = new ShopSettingsService(repository, md, g, k, clock);
        service = new ShippingQuoteService(ghtk, k, ghn, g, md, shipping, clock, measurer, settings);
    }

    private Cart cart() {
        Cart cart = Cart.builder().user(data.user("opt-user")).build();
        cart.getItems().add(CartItem.builder().cart(cart).variant(tee).quantity(1).build());
        return cart;
    }

    private ShippingQuoteService.QuoteOptions options() {
        return service.quoteOptionsResolved(CartMeasure.of(cart()), service.resolveAddress(HCM_IDS));
    }

    private void switches(boolean ghnOn, boolean ghtkOn) {
        settings.updateCarriers(new CarrierSwitchesRequest(ghnOn, ghtkOn), "boss");
    }

    private static List<ShippingSource> sources(ShippingQuoteService.QuoteOptions o) {
        return o.options().stream().map(ShippingQuote::source).toList();
    }

    @Test
    void bothQuoteCheaperIsSelectedAndOptionsAreCheapestFirst() {
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(sources(o)).containsExactly(ShippingSource.GHTK, ShippingSource.GHN);
        assertThat(o.selected()).isEqualTo(o.options().get(0));
        assertThat(o.selected().fee()).isEqualTo(32_000);
    }

    @Test
    void ghnIsSelectedWhenItIsCheaper() {
        ghn.returnFee(20_000);
        assertThat(options().selected().source()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void equalFeesSelectGhn() {
        ghn.returnFee(30_000);
        ghtk.returnFee(30_000);
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(sources(o)).containsExactly(ShippingSource.GHN, ShippingSource.GHTK);
        assertThat(o.selected().source()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void aCarrierThatCannotQuoteIsLeftOutNotAnError() {
        ghtk.fail(new com.example.identifyservice.ghtk.GhtkUnavailableException("down"));
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(sources(o)).containsExactly(ShippingSource.GHN);
        assertThat(o.selected().source()).isEqualTo(ShippingSource.GHN);

        ghtk.reset();
        ghtk.returnResult(new GhtkFeeResult(false, false, 0, "refused"));
        assertThat(sources(options())).containsExactly(ShippingSource.GHN);
    }

    @Test
    void noCarrierQuotingFallsBackToTheTableWithNoOptions() {
        ghn.failFee(new com.example.identifyservice.ghn.GhnUnavailableException("down"));
        ghtk.fail(new com.example.identifyservice.ghtk.GhtkUnavailableException("down"));
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(o.options()).isEmpty();
        assertThat(o.selected().source()).isEqualTo(ShippingSource.TABLE);
        assertThat(o.selected().estimated()).isTrue();
    }

    @Test
    void switchedOffGhnIsNeverAskedAndGhtkIsTheOnlyOption() {
        switches(false, true);
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(sources(o)).containsExactly(ShippingSource.GHTK);
        assertThat(ghn.feeCalls).isEmpty();
    }

    @Test
    void switchedOffGhtkIsNeverAsked() {
        switches(true, false);
        assertThat(sources(options())).containsExactly(ShippingSource.GHN);
        assertThat(ghtk.calls).isEmpty();
    }

    @Test
    void bothSwitchedOffUsesTheTable() {
        switches(false, false);
        ShippingQuoteService.QuoteOptions o = options();
        assertThat(o.options()).isEmpty();
        assertThat(o.selected().source()).isEqualTo(ShippingSource.TABLE);
        assertThat(o.selected().message()).isNull();
        assertThat(ghn.feeCalls).isEmpty();
        assertThat(ghtk.calls).isEmpty();
    }

    @Test
    void unconfiguredCarrierIsNeverQueriedEvenWhenSwitchedOn() {
        build(GHN_ON, GHTK_OFF);
        switches(true, true);
        assertThat(sources(options())).containsExactly(ShippingSource.GHN);
        assertThat(ghtk.calls).isEmpty();
    }

    @Test
    void aSwitchChangeClearsCachedQuotes() {
        assertThat(options().options()).hasSize(2);
        ghn.returnFee(10_000);
        assertThat(options().selected().fee()).isEqualTo(32_000);          // GHN still cached at 38.500
        switches(true, true);                                              // any change fires the listeners
        assertThat(options().selected().source()).isEqualTo(ShippingSource.GHN);
        assertThat(options().selected().fee()).isEqualTo(10_000);
    }

    @Test
    void providerConfigFollowsTheSwitchesButTheAddressModeDoesNot() {
        assertThat(service.providerConfig()).isEqualTo(new ShippingQuoteService.ProviderConfig("GHN", "GHN_IDS"));
        switches(false, true);
        assertThat(service.providerConfig()).isEqualTo(new ShippingQuoteService.ProviderConfig("GHTK", "GHN_IDS"));
        switches(false, false);
        assertThat(service.providerConfig()).isEqualTo(new ShippingQuoteService.ProviderConfig("TABLE", "GHN_IDS"));
    }

    @Test
    void quoteForCarrierReturnsTheChosenCarrierEvenWhenItIsPricier() {
        ShippingQuote q = service.quoteForCarrier(CartMeasure.of(cart()), service.resolveAddress(HCM_IDS),
                ShippingSource.GHN);
        assertThat(q.source()).isEqualTo(ShippingSource.GHN);
        assertThat(q.fee()).isEqualTo(38_500);
    }

    @Test
    void quoteForCarrierWithoutAChoiceIsTheCheapest() {
        ShippingQuote q = service.quoteForCarrier(CartMeasure.of(cart()), service.resolveAddress(HCM_IDS), null);
        assertThat(q.source()).isEqualTo(ShippingSource.GHTK);
    }

    @Test
    void quoteForCarrierThatNoLongerQuotesIsRejectedNotSwitched() {
        ghtk.fail(new com.example.identifyservice.ghtk.GhtkUnavailableException("down"));
        assertThatThrownBy(() -> service.quoteForCarrier(CartMeasure.of(cart()), service.resolveAddress(HCM_IDS),
                ShippingSource.GHTK))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SHIPPING_CARRIER_UNAVAILABLE));
    }

    @Test
    void carriersAreQuotedInParallel() {
        ghn.feeHandler = r -> {
            sleep(300);
            return new GhnFeeResult(38_500);
        };
        ghtk.handler = r -> {
            sleep(300);
            return new GhtkFeeResult(true, true, 32_000, null);
        };
        long start = System.nanoTime();
        assertThat(options().options()).hasSize(2);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(550);        // sequential would be 600+
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Fix the one existing assertion that encoded the priority chain: in `ShippingQuoteGhnTest.ghnFeeIsUsedWithWeightAndInsuranceFromTheCart` change `assertThat(ghtk.calls).isEmpty();` (line ~117) to `assertThat(ghtk.calls).hasSize(1);   // GHTK is asked as well (the fake is down by default, so GHN's fee is still the selected quote)`.

- [ ] **Step 2: Run to verify the tests fail**

Run: `mvn -q test -Dtest=ShippingQuoteOptionsTest`
Expected: COMPILATION ERROR (`quoteOptionsResolved`, `quoteForCarrier`, `QuoteOptions`, `SHIPPING_CARRIER_UNAVAILABLE` missing).

- [ ] **Step 3: Implement**

`exception/ErrorCode.java`: change the last constant's `;` to `,` and append:

```java
    SHIPPING_CARRIER_UNAVAILABLE(2024, "Đơn vị vận chuyển đã chọn hiện không khả dụng, vui lòng chọn lại", HttpStatus.CONFLICT);
```

`service/ShippingQuoteService.java`:

1. Imports to add: `java.util.ArrayList`, `java.util.Comparator`, `java.util.List`, `java.util.concurrent.CompletableFuture`, `java.util.concurrent.ExecutorService`, `java.util.concurrent.Executors`.
2. Add near the other nested types:

```java
    /** Every live quote (cheapest first, GHN first on a tie) and the default selection; options is empty on a fallback. */
    public record QuoteOptions(ShippingQuote selected, List<ShippingQuote> options) {
    }

    private enum GhtkOutcome { QUOTED, REFUSED, DOWN }

    private record GhtkAttempt(GhtkOutcome outcome, ShippingQuote quote) {
    }

    /** Small daemon pool so the two carriers are asked at the same time (waits do not add up). */
    private static final ExecutorService CARRIER_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "carrier-quote");
        t.setDaemon(true);
        return t;
    });
```
3. Replace `ghtkEnabled(...)` usage with availability helpers (keep `ghtkEnabled` for the credentials check):

```java
    private CarrierSwitches switches() {
        return settings == null ? CarrierSwitches.ALL_ON : settings.carrierSwitches();
    }

    /** GHN quotes fees only when configured and switched on. */
    private boolean ghnQuoting() {
        return ghnProps.isEnabled() && switches().ghn();
    }

    private boolean ghtkQuoting(PickupAddress pickup) {
        return ghtkEnabled(pickup) && switches().ghtk();
    }
```
4. `providerConfig()` becomes:

```java
    public ProviderConfig providerConfig() {
        String mode = masterData.isAvailable() ? "GHN_IDS" : "TEXT";
        if (ghnQuoting() && masterData.isAvailable()) return new ProviderConfig("GHN", mode);
        return new ProviderConfig(ghtkQuoting(pickup()) ? "GHTK" : "TABLE", mode);
    }
```
5. Add `quoteOptionsForCurrentCart` next to `quoteCurrentCart`:

```java
    public QuoteOptions quoteOptionsForCurrentCart(QuoteAddress address) {
        requireAddressShape(address);
        return quoteOptionsResolved(cartMeasurer.measureCurrentCart(), resolveAddress(address));
    }
```
6. Replace the whole `quoteResolved(CartMeasure, QuoteAddress)` method (from its Javadoc to its closing brace, i.e. everything up to the `/** GHN fee, or null ... */` comment of `ghnQuote`) with:

```java
    /** The default selection for an address already returned by {@link #resolveAddress}. */
    public ShippingQuote quoteResolved(CartMeasure measure, QuoteAddress address) {
        return quoteOptionsResolved(measure, address).selected();
    }

    /** The quote of the carrier the customer chose (null = the default). A carrier that no longer quotes is rejected. */
    public ShippingQuote quoteForCarrier(CartMeasure measure, QuoteAddress address, ShippingSource carrier) {
        QuoteOptions result = quoteOptionsResolved(measure, address);
        if (carrier == null) return result.selected();
        return result.options().stream().filter(q -> q.source() == carrier).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.SHIPPING_CARRIER_UNAVAILABLE));
    }

    /**
     * Asks every available carrier (GHN on this thread, GHTK on the pool) and returns the live quotes cheapest first.
     * With none, the fallback of the old chain: GHTK refused -> table or not deliverable, GHTK down -> table with the
     * GHTK message, otherwise the table (with the GHN message when GHN was tried).
     */
    public QuoteOptions quoteOptionsResolved(CartMeasure measure, QuoteAddress address) {
        int weightGrams = measure.weightGrams();
        PickupAddress pickup = pickup();
        boolean tryGhn = ghnQuoting() && address.hasGhnIds();
        boolean tryGhtk = ghtkQuoting(pickup) && address.hasTextNames();

        CompletableFuture<GhtkAttempt> ghtkFuture = tryGhtk
                ? CompletableFuture.supplyAsync(() -> ghtkAttempt(measure, address, pickup), CARRIER_POOL) : null;
        ShippingQuote ghnQuote = tryGhn ? ghnQuote(measure, address, pickup) : null;
        GhtkAttempt ghtkAttempt = ghtkFuture == null ? null : ghtkFuture.join();

        List<ShippingQuote> live = new ArrayList<>();
        if (ghnQuote != null) live.add(ghnQuote);
        if (ghtkAttempt != null && ghtkAttempt.outcome() == GhtkOutcome.QUOTED) live.add(ghtkAttempt.quote());
        live.sort(Comparator.comparingLong(ShippingQuote::fee));      // stable: GHN stays ahead on a tie
        if (!live.isEmpty()) return new QuoteOptions(live.get(0), List.copyOf(live));

        if (ghtkAttempt != null) {
            ShippingQuote fallback = ghtkAttempt.outcome() == GhtkOutcome.REFUSED
                    ? unsupportedQuote(address, weightGrams)
                    : tableQuote(address, weightGrams, MSG_DOWN_FALLBACK);
            return new QuoteOptions(fallback, List.of());
        }
        return new QuoteOptions(tableQuote(address, weightGrams, tryGhn ? MSG_GHN_DOWN_FALLBACK : null), List.of());
    }

    /** One GHTK fee attempt (cache, breaker and call exactly as before); never throws. */
    private GhtkAttempt ghtkAttempt(CartMeasure measure, QuoteAddress address, PickupAddress pickup) {
        int weightGrams = measure.weightGrams();
        String province = address.provinceName().trim();
        String ward = address.wardName().trim();
        String safeAddress = address.address() == null ? "" : address.address().trim();
        // the pick-up is wholly from the settings (when they name province and ward) or wholly from the environment
        boolean fromSettings = pickup.hasNamedOrigin();
        String pickProvince = fromSettings ? pickup.provinceName().trim() : props.pickProvince();
        String pickWard = fromSettings ? pickup.wardName().trim() : props.pickWard();
        String pickDistrict = fromSettings ? pickup.districtName() : props.pickDistrict();
        String pickAddress = fromSettings ? pickup.address() : props.pickAddress();
        String pickSignature = normalize(pickProvince) + "/" + normalize(pickDistrict) + "/" + normalize(pickWard)
                + "/" + normalize(pickAddress);
        String key = ghtkCacheKey(pickSignature, province, ward, safeAddress, weightGrams, measure.value());
        ShippingQuote cached = cacheGet(key);
        if (cached != null) return new GhtkAttempt(GhtkOutcome.QUOTED, cached);

        CarrierBreaker.Permit permit = ghtkBreaker.acquire();
        if (permit == CarrierBreaker.Permit.DENIED) return new GhtkAttempt(GhtkOutcome.DOWN, null);

        GhtkFeeResult result;
        try {
            result = ghtk.calculateFee(new GhtkFeeRequest(province, ward, safeAddress, weightGrams, measure.value(),
                    pickProvince, pickWard, pickDistrict, pickAddress));
            ghtkBreaker.success(); // a clean answer (even a refusal) means GHTK is reachable
        } catch (RuntimeException e) {
            if (!(e instanceof GhtkUnavailableException))
                log.warn("Unexpected GHTK quote failure: {}", e.getClass().getSimpleName());
            ghtkBreaker.failure();
            return new GhtkAttempt(GhtkOutcome.DOWN, null);
        } finally {
            ghtkBreaker.release(permit);
        }
        if (result.success() && result.deliverable()) {
            ShippingQuote quote = new ShippingQuote(result.fee(), ShippingSource.GHTK, false, weightGrams, true, null);
            cachePut(key, quote);
            return new GhtkAttempt(GhtkOutcome.QUOTED, quote);
        }
        return new GhtkAttempt(GhtkOutcome.REFUSED, null);
    }
```
   Also update the class Javadoc first paragraph to: "Provider chain" -> "Every available carrier (GHN with GHN ids, GHTK; each configured and switched on) is asked in parallel and the cheaper fee is the default; with none the fixed province table is used (flagged as an estimate)."

- [ ] **Step 4: Run to verify the tests pass**

Run: `mvn -q test -Dtest='ShippingQuoteOptionsTest,ShippingQuoteGhnTest,ShippingQuoteServiceTest,ShopSettingsQuoteTest'`
Expected: PASS. Any other pre-existing assertion that fails must be one that counts calls to the *other* carrier or expects GHN to win while GHTK also quotes; adjust only such call-count/priority assertions (the fakes default to "down", so most tests are unaffected). Do not weaken any other assertion.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: quote every available carrier, cheapest is the default, switches decide availability

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: API: `options` in the quote response, `carrier` on checkout

**Files:**
- Modify: `dto/response/ShippingQuoteResponse.java`, `controller/ShippingController.java`, `dto/request/CheckoutRequest.java`, `service/OrderService.java`
- Create: `src/test/java/com/example/identifyservice/service/OrderCheckoutCarrierTest.java`
- Modify (tests): `controller/ShippingQuoteControllerTest.java`, `controller/CheckoutShippingTest.java`

**Interfaces:**
- Consumes: `ShippingQuoteService.QuoteOptions`, `quoteOptionsForCurrentCart`, `quoteForCarrier` (Task 2).
- Produces: JSON `result.options: [{source, fee, estimated}]` on `POST /shipping/quote`; `CheckoutRequest.carrier` (String `GHN`|`GHTK`, optional) and `CheckoutRequest.shippingCarrier(): ShippingSource` (null when absent).

- [ ] **Step 1: Write the failing tests**

`OrderCheckoutCarrierTest.java` (same fixtures as `OrderCheckoutGhnTest`):

```java
package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.CheckoutRequest;
import com.example.identifyservice.dto.response.OrderResponse;
import com.example.identifyservice.entity.Product;
import com.example.identifyservice.entity.ProductVariant;
import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghtk.GhtkUnavailableException;
import com.example.identifyservice.repository.ProductVariantRepository;
import com.example.identifyservice.testsupport.FakeGhnGateway;
import com.example.identifyservice.testsupport.FakeGhtkGateway;
import com.example.identifyservice.testsupport.TestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCheckoutCarrierTest {
    @Autowired OrderService orders;
    @Autowired CartService cart;
    @Autowired TestDataFactory data;
    @Autowired ProductVariantRepository variants;
    @Autowired EntityManager em;
    @Autowired FakeGhnGateway ghn;
    @Autowired FakeGhtkGateway ghtk;
    @Autowired ShippingQuoteService quoteService;

    ProductVariant m;

    @BeforeEach
    void setUp() {
        resetFakes();
        ghn.useSampleData();
        ghn.returnFee(37_000);
        ghtk.returnFee(31_000);
        data.user("carol");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("carol", "x", "ROLE_USER"));
        m = data.variant(data.product("carrier-tee", 200_000, true), "M", "white", 5, null);
        cart.addItem(m.getId(), 1);
    }

    @AfterEach
    void tearDown() {
        resetFakes();
        SecurityContextHolder.clearContext();
    }

    private void resetFakes() {
        ghn.reset();
        ghtk.reset();
        quoteService.clearCache();
    }

    private CheckoutRequest request(String carrier) {
        return new CheckoutRequest("Carol", "0901234567", "carol@example.com", "12 Nguyen Hue", null, null, null,
                PaymentMethod.COD, 202, 1442, "20308", null, carrier);
    }

    @Test
    void withoutACarrierTheCheaperOneIsUsed() {
        OrderResponse order = orders.checkout(request(null));
        assertThat(order.shippingSource()).isEqualTo(ShippingSource.GHTK);
        assertThat(order.shippingFee()).isEqualTo(31_000);
        assertThat(order.total()).isEqualTo(231_000);
    }

    @Test
    void theCustomersChoiceIsStoredEvenWhenItIsPricier() {
        OrderResponse order = orders.checkout(request("GHN"));
        assertThat(order.shippingSource()).isEqualTo(ShippingSource.GHN);
        assertThat(order.shippingFee()).isEqualTo(37_000);
        assertThat(order.total()).isEqualTo(237_000);
    }

    @Test
    void equalFeesDefaultToGhn() {
        ghtk.returnFee(37_000);
        assertThat(orders.checkout(request(null)).shippingSource()).isEqualTo(ShippingSource.GHN);
    }

    @Test
    void chosenCarrierThatStoppedQuotingIsRejectedAndNothingChanges() {
        ghtk.fail(new GhtkUnavailableException("down"));
        assertThatThrownBy(() -> orders.checkout(request("GHTK"))).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SHIPPING_CARRIER_UNAVAILABLE);
        em.flush();
        em.clear();
        assertThat(variants.findById(m.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(orders.myOrders(0, 10).items()).isEmpty();
        assertThat(cart.getCart().items()).hasSize(1);
    }
}
```

Add to `ShippingQuoteControllerTest` (add `@Autowired FakeGhnGateway ghn;` with import `com.example.identifyservice.testsupport.FakeGhnGateway`, call `ghn.reset();` in both `setUp()` and `resetGhtk()` next to the `ghtk.reset()` calls):

```java
    static final String IDS_BODY = "{\"provinceId\":202,\"districtId\":1442,\"wardCode\":\"20308\",\"address\":\"12 Nguyen Hue\"}";

    @Test
    void bothCarriersAreListedCheapestFirstAndTheDefaultIsTheCheapest() throws Exception {
        cartWith("opt-tee", 1);
        ghn.useSampleData();
        ghn.returnFee(38_500);
        ghtk.returnFee(32_000);

        call(IDS_BODY).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.fee").value(32000))
                .andExpect(jsonPath("$.result.source").value("GHTK"))
                .andExpect(jsonPath("$.result.options.length()").value(2))
                .andExpect(jsonPath("$.result.options[0].source").value("GHTK"))
                .andExpect(jsonPath("$.result.options[0].fee").value(32000))
                .andExpect(jsonPath("$.result.options[0].estimated").value(false))
                .andExpect(jsonPath("$.result.options[1].source").value("GHN"))
                .andExpect(jsonPath("$.result.options[1].fee").value(38500));
    }

    @Test
    void singleCarrierQuoteHasOneOptionAndTheTableHasNone() throws Exception {
        cartWith("opt-tee2", 1);
        ghtk.returnFee(33_000);
        call(BODY).andExpect(jsonPath("$.result.options.length()").value(1))
                .andExpect(jsonPath("$.result.options[0].source").value("GHTK"));

        ghtk.reset();
        quoteService.clearCache();
        call(BODY).andExpect(jsonPath("$.result.source").value("TABLE"))
                .andExpect(jsonPath("$.result.options.length()").value(0));
    }
```

Add to `CheckoutShippingTest`:

```java
    @Test
    void unknownOrTableCarrierIsInvalidInput() throws Exception {
        cartWith("carrier-tee3");
        checkout("Hà Nội", ",\"carrier\":\"TABLE\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
        checkout("Hà Nội", ",\"carrier\":\"FOO\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1011));
    }
```

- [ ] **Step 2: Run to verify the tests fail**

Run: `mvn -q test -Dtest='OrderCheckoutCarrierTest,ShippingQuoteControllerTest,CheckoutShippingTest'`
Expected: COMPILATION ERROR (13-argument `CheckoutRequest`), then, once it compiles, FAIL on `options`.

- [ ] **Step 3: Implement**

`dto/response/ShippingQuoteResponse.java` (replace the file body; `from(ShippingQuote)` is only used by the controller):

```java
package com.example.identifyservice.dto.response;

import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.service.ShippingQuoteService.QuoteOptions;

import java.util.List;

/**
 * The top-level fields describe the default selection (the cheapest live carrier, or the table fallback);
 * {@code options} lists every live carrier cheapest first (never TABLE, empty on a fallback).
 */
public record ShippingQuoteResponse(long fee, ShippingSource source, boolean estimated, int weightGrams,
                                    boolean deliverable, String message, List<Option> options) {
    public record Option(ShippingSource source, long fee, boolean estimated) {
    }

    public static ShippingQuoteResponse from(QuoteOptions result) {
        ShippingQuote q = result.selected();
        List<Option> options = result.options().stream()
                .map(o -> new Option(o.source(), o.fee(), o.estimated())).toList();
        return new ShippingQuoteResponse(q.fee(), q.source(), q.estimated(), q.weightGrams(), q.deliverable(),
                q.message(), options);
    }
}
```

`controller/ShippingController.java` quote method body:

```java
        return ApiResponse.ok(ShippingQuoteResponse.from(
                shippingQuoteService.quoteOptionsForCurrentCart(request.toAddress())));
```

`dto/request/CheckoutRequest.java`: add a 13th component and keep the shorter constructors:

```java
        @Size(max = 100, message = "INVALID_INPUT") String district,
        @Pattern(regexp = "^(GHN|GHTK)$", message = "INVALID_INPUT") String carrier) {

    /** Text-address request (no GHN ids). */
    public CheckoutRequest(String receiverName, String phone, String email, String address, String province,
                           String ward, String note, PaymentMethod paymentMethod) {
        this(receiverName, phone, email, address, province, ward, note, paymentMethod, null, null, null, null, null);
    }

    /** Request without a carrier choice (the cheapest carrier is used). */
    public CheckoutRequest(String receiverName, String phone, String email, String address, String province,
                           String ward, String note, PaymentMethod paymentMethod, Integer provinceId,
                           Integer districtId, String wardCode, String district) {
        this(receiverName, phone, email, address, province, ward, note, paymentMethod, provinceId, districtId,
                wardCode, district, null);
    }

    /** The chosen carrier, or null for the default (cheapest). */
    public ShippingSource shippingCarrier() {
        return carrier == null ? null : ShippingSource.valueOf(carrier);
    }
```
   (add `import com.example.identifyservice.enums.ShippingSource;`; keep `toAddress()`; update the class Javadoc with: "{@code carrier}: optional GHN or GHTK chosen by the customer; the server re-quotes that carrier".)

`service/OrderService.java`: replace
`ShippingQuote quote = shippingQuoteService.quoteResolved(CartMeasure.of(cart), resolved);` with
`ShippingQuote quote = shippingQuoteService.quoteForCarrier(CartMeasure.of(cart), resolved, request.shippingCarrier());` and update the nearby comment to "(the customer's chosen carrier, else the cheapest of GHN and GHTK, else the table)".

- [ ] **Step 4: Run to verify the tests pass**

Run: `mvn -q test -Dtest='OrderCheckoutCarrierTest,ShippingQuoteControllerTest,CheckoutShippingTest,OrderCheckoutGhnTest,OrderServiceTest'`
Expected: PASS. Then the whole backend: `mvn -q clean test` -> PASS (fix only compile fallout and call-count/priority assertions, as in Task 2).

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "feat: quote response lists carrier options, checkout accepts the customer's carrier

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Checkout UI: choose the carrier

**Files:**
- Modify: `shop-ui/src/utils/shipping.js`, `shop-ui/src/utils/shipping.test.js`
- Create: `shop-ui/src/components/CarrierChoice.jsx`
- Modify: `shop-ui/src/pages/Checkout.jsx`, `shop-ui/src/pages/Checkout.css`

**Interfaces:**
- Consumes: quote JSON with `options: [{source, fee, estimated}]` (Task 3); order payload accepts `carrier` (Task 3); error code `2024`.
- Produces (utils/shipping.js): `CARRIER_NAME`, `quoteOptions(quote)`, `hasCarrierChoice(quote)`, `chosenOption(quote, picked)`, `applyCarrier(quote, picked)`, `carrierForOrder(quote, picked)`.

- [ ] **Step 1: Write the failing tests**

In `shipping.test.js` extend the import with `applyCarrier, carrierForOrder, chosenOption, hasCarrierChoice, quoteOptions`, and add:

```js
const two = {
  fee: 32000, source: 'GHTK', estimated: false, weightGrams: 600, deliverable: true, message: null,
  options: [
    { source: 'GHTK', fee: 32000, estimated: false },
    { source: 'GHN', fee: 38500, estimated: false },
  ],
};
const one = { ...ghn, options: [{ source: 'GHN', fee: 28000, estimated: false }] };

describe('carrier options', () => {
  it('quoteOptions keeps live carriers only and tolerates a missing list', () => {
    expect(quoteOptions(two).map((o) => o.source)).toEqual(['GHTK', 'GHN']);
    expect(quoteOptions({ ...two, options: [{ source: 'TABLE', fee: 1, estimated: true }] })).toEqual([]);
    expect(quoteOptions(ghn)).toEqual([]);
    expect(quoteOptions(null)).toEqual([]);
  });

  it('there is a choice only with two or more options', () => {
    expect(hasCarrierChoice(two)).toBe(true);
    expect(hasCarrierChoice(one)).toBe(false);
    expect(hasCarrierChoice(table)).toBe(false);
  });

  it('chosenOption keeps the pick while it is offered, else the cheapest', () => {
    expect(chosenOption(two, null).source).toBe('GHTK');
    expect(chosenOption(two, 'GHN').source).toBe('GHN');
    expect(chosenOption(one, 'GHTK').source).toBe('GHN');   // picked carrier no longer offered
    expect(chosenOption(table, 'GHN')).toBeNull();
  });

  it('applyCarrier makes the summary follow the pick and leaves single quotes alone', () => {
    expect(applyCarrier(two, 'GHN')).toMatchObject({ source: 'GHN', fee: 38500, estimated: false, weightGrams: 600 });
    expect(applyCarrier(two, null)).toMatchObject({ source: 'GHTK', fee: 32000 });
    expect(applyCarrier(one, 'GHN')).toBe(one);
    expect(applyCarrier(null, 'GHN')).toBeNull();
  });

  it('carrierForOrder is sent only when the customer saw a choice', () => {
    expect(carrierForOrder(two, 'GHN')).toBe('GHN');
    expect(carrierForOrder(two, null)).toBe('GHTK');
    expect(carrierForOrder(one, 'GHN')).toBeUndefined();
    expect(carrierForOrder(table, null)).toBeUndefined();
  });

  it('the summary total follows the picked carrier', () => {
    const picked = applyCarrier(two, 'GHN');
    const ship = shippingDisplay({ state: 'ready', quote: picked, tableFee: 0, province: '', mode: 'GHN_IDS' });
    expect(ship.fee).toBe(38500);
    expect(ship.label).toBe('Phí GHN');
    expect(orderTotal(500000, ship.fee)).toBe(538500);
  });
});
```

- [ ] **Step 2: Run to verify the tests fail**

Run: `cd shop-ui && npx vitest run src/utils/shipping.test.js`
Expected: FAIL (`quoteOptions is not a function` / import error).

- [ ] **Step 3: Implement**

Append to `shop-ui/src/utils/shipping.js`:

```js
export const CARRIER_NAME = { GHN: 'GHN', GHTK: 'GHTK' };

/** Live carrier options of a quote (the server lists them cheapest first); [] when the fee is the fixed table. */
export function quoteOptions(quote) {
  return Array.isArray(quote?.options) ? quote.options.filter((o) => CARRIER_NAME[o?.source]) : [];
}

/** True when the customer has a real choice (two or more live carriers). */
export const hasCarrierChoice = (quote) => quoteOptions(quote).length >= 2;

/** The option in use: the customer's pick while it is still offered, else the cheapest (first); null without options. */
export function chosenOption(quote, picked) {
  const options = quoteOptions(quote);
  return options.find((o) => o.source === picked) ?? options[0] ?? null;
}

/** The quote with the chosen option's fee and source applied, so the summary and the total follow the pick. */
export function applyCarrier(quote, picked) {
  if (!quote || !hasCarrierChoice(quote)) return quote;
  const o = chosenOption(quote, picked);
  return { ...quote, source: o.source, fee: o.fee, estimated: o.estimated };
}

/** `carrier` for POST /orders: only sent when the customer saw a choice (otherwise the server picks the cheapest). */
export function carrierForOrder(quote, picked) {
  return hasCarrierChoice(quote) ? chosenOption(quote, picked).source : undefined;
}
```

`shop-ui/src/components/CarrierChoice.jsx`:

```jsx
import RadioCards from './ui/RadioCards.jsx';
import { formatVnd } from '../utils/money.js';
import { CARRIER_NAME } from '../utils/shipping.js';

/** Radio cards for the live carriers of a quote; renders nothing unless there are two or more (cheapest first). */
export default function CarrierChoice({ options, value, onChange, disabled = false }) {
  if (options.length < 2) return null;
  const cards = options.map((o, i) => ({
    value: o.source,
    icon: <strong className="co-carrier__abbr">{CARRIER_NAME[o.source]}</strong>,
    title: CARRIER_NAME[o.source],
    description: `${formatVnd(o.fee)}${i === 0 ? ' · Rẻ nhất' : ''}`,
  }));
  return (
    <div className="co-carriers">
      <p className="co-carriers__title">Đơn vị vận chuyển</p>
      <RadioCards label="Đơn vị vận chuyển" value={value} onChange={onChange} options={cards} disabled={disabled} />
    </div>
  );
}
```

`Checkout.css` (append):

```css
.co-carriers { display: grid; gap: 10px; margin: 14px 0 4px; }
.co-carriers__title { font-size: var(--fs-small); font-weight: 600; }
.co-carrier__abbr { font-size: 11px; letter-spacing: 0.02em; }
```

`Checkout.jsx`:
1. Import: add `applyCarrier, carrierForOrder, chosenOption, hasCarrierChoice, quoteOptions` to the `../utils/shipping.js` import and `import CarrierChoice from '../components/CarrierChoice.jsx';` after the `Badge` import.
2. Replace the `const quote = useShippingQuote({...}); const ship = ...` block with:

```js
  const [pickedCarrier, setPickedCarrier] = useState(null);
  const [quoteNonce, setQuoteNonce] = useState(0);   // bumped to force a fresh quote after the server refused our carrier
  const quote = useShippingQuote({
    address: addressPayload,
    cartKey: `${cartKeyOf(cart)}:${quoteNonce}`,
    enabled: loaded && !configLoading && !placed && cart.items.length > 0 && isAddressComplete(mode, form, selections),
  });
  const chosen = chosenOption(quote.quote, pickedCarrier);
  const ship = shippingDisplay({
    state: quote.state, quote: applyCarrier(quote.quote, pickedCarrier), tableFee, province: form.province, mode,
  });
```
   (keep the lines after `const shippingFee = ship?.fee ?? 0;` unchanged.)
3. In `submit`, replace `const payload = normalizeCheckoutForm(form, addr);` with
   `const payload = { ...normalizeCheckoutForm(form, addr), carrier: carrierForOrder(quote.quote, pickedCarrier) };`
   and in the `catch`, before the `if (err.code === 2018)` block, insert:

```js
      if (err.code === 2024) {   // the chosen carrier stopped quoting: start over with a fresh quote and the cheapest
        setPickedCarrier(null);
        setQuoteNonce((n) => n + 1);
        return setError(err.message);
      }
```
4. In the summary, right after the closing `</dl>` of `.co-rows` and before `<div className="co-total">`, add:

```jsx
          {hasCarrierChoice(quote.quote) && (
            <CarrierChoice options={quoteOptions(quote.quote)} value={chosen.source} onChange={setPickedCarrier} disabled={busy} />
          )}
```

- [ ] **Step 4: Run to verify the tests pass**

Run: `cd shop-ui && npx vitest run`
Expected: PASS (all files). Then `npm run build` -> succeeds.

- [ ] **Step 5: Commit**

```bash
git add shop-ui/src
git commit -m "feat(ui): checkout lets the customer pick GHN or GHTK, cheapest preselected

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Admin UI: carrier switches

**Files:**
- Modify: `shop-ui/src/utils/settings.js`, `shop-ui/src/utils/settings.test.js`, `shop-ui/src/pages/admin/AdminSettings.jsx`, `shop-ui/src/pages/admin/Admin.css`

**Interfaces:**
- Consumes: `GET /admin/settings/shop` -> `carriers: {ghn: {configured, enabled, shopId}, ghtk: {configured, enabled}}`; `PUT /admin/settings/carriers` `{ghn, ghtk}` -> same settings shape (Task 1).
- Produces (utils/settings.js): `carrierRows(carriers)` rows `{key, label, configured, on, state: 'on'|'off'|'missing', detail, hint}`; `CARRIER_STATE_LABEL`; `carrierSwitchBody(carriers, key, on)`; `allCarriersOff(carriers)`; `ALL_OFF_NOTE`.

- [ ] **Step 1: Write the failing tests**

In `settings.test.js` replace the existing `carrierRows explains how to enable a carrier` test with the following (extend the import list with `CARRIER_STATE_LABEL, allCarriersOff, carrierSwitchBody`):

```js
  it('carrierRows tells missing, on and off apart', () => {
    const rows = carrierRows({
      ghn: { configured: true, enabled: false, shopId: '123' },
      ghtk: { configured: false, enabled: true },
    });
    expect(rows[0]).toMatchObject({ key: 'ghn', configured: true, on: false, state: 'off', detail: 'Shop ID 123', hint: '' });
    expect(rows[1]).toMatchObject({
      key: 'ghtk', configured: false, on: false, state: 'missing', hint: 'Thêm GHTK_TOKEN vào .env để bật GHTK',
    });
    expect(carrierRows({ ghn: { configured: false, enabled: true } })[0].hint).toBe('Thêm GHN_SHOP_ID vào .env để bật GHN');
    const on = carrierRows({ ghn: { configured: true, enabled: true, shopId: null }, ghtk: { configured: true } });
    expect(on[0]).toMatchObject({ state: 'on', on: true, detail: '' });
    expect(on[1]).toMatchObject({ state: 'on', on: true });   // a missing switch value means on
    expect(CARRIER_STATE_LABEL).toEqual({ on: 'Đang bật', off: 'Đã tắt', missing: 'Chưa cấu hình' });
  });

  it('carrierSwitchBody flips one carrier and keeps the other stored value', () => {
    const carriers = { ghn: { configured: true, enabled: true }, ghtk: { configured: true, enabled: false } };
    expect(carrierSwitchBody(carriers, 'ghn', false)).toEqual({ ghn: false, ghtk: false });
    expect(carrierSwitchBody(carriers, 'ghtk', true)).toEqual({ ghn: true, ghtk: true });
    expect(carrierSwitchBody(null, 'ghn', false)).toEqual({ ghn: false, ghtk: true });
  });

  it('allCarriersOff is true when nothing can quote (off or not configured)', () => {
    expect(allCarriersOff({ ghn: { configured: true, enabled: false }, ghtk: { configured: false, enabled: true } })).toBe(true);
    expect(allCarriersOff({ ghn: { configured: true, enabled: true }, ghtk: { configured: false } })).toBe(false);
  });
```

- [ ] **Step 2: Run to verify the tests fail**

Run: `cd shop-ui && npx vitest run src/utils/settings.test.js`
Expected: FAIL.

- [ ] **Step 3: Implement**

`utils/settings.js`: replace the `carrierRows` function (and its comment) with:

```js
export const CARRIER_STATE_LABEL = { on: 'Đang bật', off: 'Đã tắt', missing: 'Chưa cấu hình' };
export const ALL_OFF_NOTE = 'Cả hai đơn vị đều đang tắt hoặc chưa cấu hình, hệ thống dùng bảng phí cố định theo tỉnh.';

function carrierRow(key, label, carrier, detail, missingHint) {
  const configured = Boolean(carrier.configured);
  const on = configured && carrier.enabled !== false;   // a missing switch value means on
  return { key, label, configured, on, state: !configured ? 'missing' : on ? 'on' : 'off', detail, hint: configured ? '' : missingHint };
}

/** Status card rows for the carriers ({key, label, configured, on, state, detail, hint}). */
export function carrierRows(carriers) {
  const ghn = carriers?.ghn ?? {};
  const ghtk = carriers?.ghtk ?? {};
  return [
    carrierRow('ghn', 'GHN', ghn, ghn.configured && ghn.shopId ? `Shop ID ${ghn.shopId}` : '', 'Thêm GHN_SHOP_ID vào .env để bật GHN'),
    carrierRow('ghtk', 'GHTK', ghtk, '', 'Thêm GHTK_TOKEN vào .env để bật GHTK'),
  ];
}

/** Body of PUT /admin/settings/carriers after flipping one carrier (the other keeps its stored switch). */
export function carrierSwitchBody(carriers, key, on) {
  return { ghn: carriers?.ghn?.enabled !== false, ghtk: carriers?.ghtk?.enabled !== false, [key]: on };
}

/** True when no carrier can quote (every one is off or not configured), so the fixed province table is used. */
export const allCarriersOff = (carriers) => carrierRows(carriers).every((r) => !r.on);
```

`pages/admin/AdminSettings.jsx`:
1. Imports: add `import Switch from '../../components/ui/Switch.jsx';` and extend the `../../utils/settings.js` import with `ALL_OFF_NOTE, CARRIER_STATE_LABEL, allCarriersOff, carrierSwitchBody`.
2. Replace the `CarrierCard` function with:

```jsx
function CarrierCard({ carriers }) {
  const { toast } = useToast();
  const [current, setCurrent] = useState(carriers);   // own state: a switch saves on click and must not touch the shop form
  const [pending, setPending] = useState('');
  useEffect(() => { setCurrent(carriers); }, [carriers]);
  const rows = carrierRows(current);

  async function toggle(key, on) {
    if (pending) return;
    setPending(key);
    try {
      const saved = await api('PUT', '/admin/settings/carriers', carrierSwitchBody(current, key, on));
      setCurrent(saved.carriers);
    } catch (err) {
      toast(err.message || 'Không lưu được thay đổi, vui lòng thử lại', { tone: 'danger' });   // the switch reverts below
    } finally {
      setPending('');
    }
  }

  return (
    <aside className="ad-card ad-set__card ad-set__side" aria-labelledby="ad-set-carriers">
      <h2 id="ad-set-carriers" className="ad-set__title">Đơn vị vận chuyển</h2>
      <ul className="ad-carriers">
        {rows.map((c) => (
          <li key={c.key} className="ad-carrier">
            <div className="ad-carrier__row">
              <strong>{c.label}</strong>
              <span className="ad-carrier__ctl">
                <Badge tone={c.state === 'on' ? 'success' : 'neutral'} dot>{CARRIER_STATE_LABEL[c.state]}</Badge>
                <Switch
                  hideLabel
                  label={`Bật ${c.label}`}
                  checked={pending === c.key ? !c.on : c.on}
                  disabled={!c.configured || Boolean(pending)}
                  onChange={(on) => toggle(c.key, on)}
                />
              </span>
            </div>
            {c.detail && <p className="ad-muted">{c.detail}</p>}
            {c.hint && <p className="ad-muted">{c.hint}</p>}
          </li>
        ))}
      </ul>
      {allCarriersOff(current) && <p className="ad-muted" role="status">{ALL_OFF_NOTE}</p>}
      <p className="ad-set__note">{NOTE}</p>
    </aside>
  );
}
```

`Admin.css` (after the `.ad-carrier__row` rule):

```css
.ad-carrier__ctl { display: inline-flex; align-items: center; gap: 10px; }
```

- [ ] **Step 4: Run to verify the tests pass**

Run: `cd shop-ui && npx vitest run`
Expected: PASS. Then `npm run build` -> succeeds.

- [ ] **Step 5: Commit**

```bash
git add shop-ui/src
git commit -m "feat(ui): admin switches to turn GHN and GHTK on or off

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Migrate, document and verify live

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Back up and migrate the local database**

Run (restart nothing yet; the app must not be running during the schema change):
`python scripts/dbtool.py backup` then `python scripts/dbtool.py run migration_v5_carrier_toggle.sql`
Expected: both succeed. Confirm with the MCP `identity-db` tool or `SHOW COLUMNS FROM shop_settings LIKE '%_enabled'`: two `tinyint(1)` columns, `NOT NULL`, default 1.

- [ ] **Step 2: Restart the backend and check the log**

Stop the running `mvn spring-boot:run`, run `mvn clean compile` then `mvn spring-boot:run`. Expected: the app starts and the log contains no `Error executing DDL`.

- [ ] **Step 3: Live API checks (admin token from `/auth/token`)**

1. `GET /admin/settings/shop` -> `carriers.ghn = {configured:true, enabled:true, shopId:...}`, `carriers.ghtk = {configured:false, enabled:true}`.
2. `PUT /admin/settings/carriers {"ghn":false,"ghtk":true}` -> 200 with `ghn.enabled=false`; `POST /shipping/quote` as a user with a cart now answers with `source: TABLE`, `options: []`.
3. `PUT /admin/settings/carriers {"ghn":true,"ghtk":true}` -> restores GHN; a quote answers `source: GHN` with one option.
Restore the switches to both on afterwards.

- [ ] **Step 4: Browser check with Playwright (script in the scratchpad, `channel: 'chrome'`, against `http://localhost:5173`)**

1. Admin `/admin/settings`: GHN switch is on and enabled, GHTK switch is disabled with the "Thêm GHTK_TOKEN" hint. Click the GHN switch: it flips, a request `PUT /identity/admin/settings/carriers` returns 200, the card shows "Đã tắt" and the "bảng phí cố định" note; reload keeps it; click again to turn it back on.
2. Failure path: with `page.route('**/admin/settings/carriers', r => r.abort())`, click a switch: it reverts and a danger toast appears.
3. Checkout with two options (GHTK is not configured in this environment, so intercept): `page.route('**/shipping/quote', ...)` fulfilling the real response plus `options: [{source:'GHTK',fee:32000,estimated:false},{source:'GHN',fee:38500,estimated:false}]` and `fee/source` of the GHTK option. Check: two radio cards, GHTK checked with "Rẻ nhất", total uses 32.000; pick GHN, total changes to include 38.500 and the fee badge reads "Phí GHN"; the captured `POST /orders` body has `"carrier":"GHN"`.
4. With the intercepted order response `route.fulfill({status:409, body: {code:2024, message:'...'}})`, submitting shows the message and the choice returns to the cheapest.
Do not place a real order.

- [ ] **Step 5: Update CLAUDE.md**

In the "GHN shipping fee" paragraph replace "priority GHN -> GHTK -> fixed table" with: "every available carrier (configured in `.env` and switched on in `/admin/settings`) is quoted in parallel; the cheaper one (GHN on a tie) is the default and the customer may choose another (`carrier` on `POST /orders`, `options` on `POST /shipping/quote`); with none available the fixed table is used." Add: "Carrier switches: `shop_settings.ghn_enabled|ghtk_enabled` (default on), `PUT /admin/settings/carriers`, run `migration_v5_carrier_toggle.sql` (after a backup) on existing databases. A chosen carrier that stops quoting gives error 2024 (409)." Add `SHIPPING_CARRIER_UNAVAILABLE` to the error-code note ("Error codes 2020-2023" is the upload group; mention 2024 in the shipping paragraph only).

- [ ] **Step 6: Full verification and push**

Run: `mvn -q clean test` and `cd shop-ui && npx vitest run && npm run build`
Expected: all PASS.

```bash
git add CLAUDE.md
git commit -m "docs: carrier choice and carrier switches

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
git push
```
