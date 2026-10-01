# Admin Statistics and GHTK Shipping Fee - Design Spec

Date: 2026-10-01
Status: Approved in chat by the user ("làm hết đi", answering the proposal below). Written spec not yet reviewed line by line; treat as the binding design.
Builds on: `docs/superpowers/specs/2026-09-30-clothing-shop-design.md` (shop) and the UI redesign brief.

## 0. Decisions confirmed by the user

- Build **statistics first**, then **GHTK shipping fee**.
- GHTK: the user will create the GHTK account/token themselves and report back. The code must be fully built and tested with mocks, work with **no token configured** (falls back to the fixed province table), and activate by filling `.env`.
- GHTK scope is **fee calculation only** (no shipment/order creation on GHTK). Fallback to the fixed table when GHTK fails, is not configured, or does not deliver to the address; show it as an estimate. Product weight in grams (default 300 g). Shop pick-up location comes from `.env` (the user will provide it).
- Statistics: revenue counted on **paid** orders (MoMo `PAID`, or COD completed), cancelled excluded; product **cost price** (optional) to compute gross profit; charts drawn as inline SVG (no chart library) following the dataviz skill.
- Push to GitHub after each finished piece (standing user preference).

## 1. Part A - Admin statistics ("Tổng quan")

### 1.1 Definitions (all dates in `Asia/Ho_Chi_Minh`)
- **Revenue order**: `paymentStatus = PAID` and `status != CANCELLED`, dated by `paidAt`. (COD orders get `paidAt` when completed; MoMo orders when payment is confirmed.)
- **Revenue** = sum of `subtotal` (goods value, excludes shipping) of revenue orders. **Shipping collected** = sum of `shippingFee` of revenue orders.
- **Orders** = orders *created* in the period (`createdAt`), any status. **Paid orders** = count of revenue orders. **Average order value** = revenue / paid orders (0 when none).
- **Cancelled orders** = orders created in the period with `status = CANCELLED`; **cancel rate** = cancelled / orders (0..1; 0 when no orders).
- **New customers** = users with `createdAt` in the period who do not have the ADMIN role.
- **Items sold** = sum of item quantities of revenue orders.
- **Profit (gross)** = sum over items of revenue orders with a known `unitCost` of `(unitPrice - unitCost) * quantity`; **coverage** = share (0..1) of the revenue-order item value (`unitPrice * quantity`) whose cost is known. Profit is `null` when no item has a cost. Shipping, payment fees and refunds are not part of it.
- **Previous period** = the same number of days immediately before `from`; every KPI is returned with its previous value so the UI can show deltas.

### 1.2 Data changes
- `Product.costPrice` (`Long`, nullable, >= 0): optional cost per unit, editable in the admin product form ("Giá vốn"). **Never exposed on public or customer endpoints** (public product list/detail, cart, orders keep their current JSON; admin detail adds `costPrice`).
- `OrderItem.unitCost` (`Long`, nullable): snapshot of the product's `costPrice` taken at checkout (existing items stay `null`).
- No migration SQL needed beyond what `ddl-auto: update` creates (nullable columns); still follow the CLAUDE.md database workflow when applying to MySQL (backup, inspect, restart, check for `Error executing DDL`).

### 1.3 API (ADMIN only, context path `/identity`)
`GET /admin/stats/overview?from=YYYY-MM-DD&to=YYYY-MM-DD&groupBy=day|month|year` (both dates inclusive; defaults: last 30 days, `groupBy=day`). Validation: `from <= to`, range <= 1100 days, `groupBy=day` only when range <= 366 days (else `INVALID_INPUT`). Response (`ApiResponse` wrapper, records):

```json
{
  "from": "2026-09-01", "to": "2026-09-30", "groupBy": "day",
  "previousFrom": "2026-08-02", "previousTo": "2026-08-31",
  "kpis": {
    "revenue": {"value": 0, "previous": 0},
    "orders": {"value": 0, "previous": 0},
    "paidOrders": {"value": 0, "previous": 0},
    "averageOrderValue": {"value": 0, "previous": 0},
    "newCustomers": {"value": 0, "previous": 0},
    "cancelledOrders": {"value": 0, "previous": 0},
    "cancelRate": {"value": 0.0, "previous": 0.0},
    "itemsSold": {"value": 0, "previous": 0},
    "shippingCollected": {"value": 0, "previous": 0},
    "profit": {"value": null, "previous": null, "coverage": 0.0}
  },
  "series": [{"bucket": "2026-09-01", "revenue": 0, "orders": 0, "paidOrders": 0, "profit": null}],
  "statusBreakdown": [{"status": "COMPLETED", "count": 0}],
  "paymentBreakdown": [{"method": "MOMO", "orders": 0, "revenue": 0}],
  "topProducts": [{"productName": "", "quantity": 0, "revenue": 0, "profit": null}],
  "lowStock": [{"productName": "", "size": "", "color": "", "sku": "", "stock": 0}]
}
```
- `series` is zero-filled for every bucket in the range (`bucket` = `YYYY-MM-DD` for day, `YYYY-MM` for month, `YYYY` for year; first/last buckets may be partial). `statusBreakdown` lists every `OrderStatus` (zero-filled). `paymentBreakdown` lists both methods. `topProducts` = top 10 by revenue (grouped by the order-item product name snapshot). `lowStock` = active variants of active products with `stock <= 5`, lowest first, max 10.
- Aggregation is done in Java over the rows fetched for the period (portable between MySQL and H2; shop-scale data), using the Vietnam time zone for bucketing.

### 1.4 Frontend (admin)
- New first admin nav item **Tổng quan** at `/admin/overview` (and `/admin` redirects there). Same design system as the rest of the admin (tokens, `Badge`, skeletons, `EmptyState`).
- **Toolbar**: presets (Hôm nay, 7 ngày, 30 ngày, Tháng này, Năm nay), custom from/to date inputs, group by Ngày/Tháng/Năm (segmented control; day disabled for ranges > 366 days), "so với kỳ trước" always on.
- **KPI cards** (revenue, paid orders, orders, average order value, new customers, items sold, cancel rate, profit with a coverage note): value, delta vs previous period with direction + semantic color (cancel rate up = bad), tiny sparkline from `series` where available.
- **Charts** (inline SVG, framer-motion draw/enter animation, hover crosshair + tooltip, keyboard-focusable points, `<details>` data table alternative per chart): revenue over time (area/line), orders over time (bars) as separate small-multiple charts (never dual axis), order status breakdown and payment method split (bars or a donut with <= 5 slices, per the dataviz skill's form guidance), top products (horizontal bars by revenue, quantity in the label), low-stock table.
- Colors: from the design tokens; categorical palette validated with the dataviz skill's `validate_palette.js` (light surface = `--surface`); status colors reserved for semantics; text never in series color.
- States: loading skeleton, error with retry, empty period ("Chưa có đơn hàng trong kỳ này"), ignore flags on fetch effects, dates validated client-side.

### 1.5 Tasks
- **S1 Backend: cost price.** `Product.costPrice`, admin request/response (admin only), `OrderItem.unitCost` snapshot in `OrderService.checkout`, tests (cost never in public JSON; snapshot taken; null stays null).
- **S2 Backend: stats.** `StatsService` + `StatsController` + records per 1.3, `ErrorCode` reuse (`INVALID_INPUT`), `TestDataFactory` helpers for dated paid/cancelled orders, tests for every definition in 1.1 incl. period boundaries at Vietnam midnight, previous period, zero-filled series, month/year grouping, profit/coverage, admin-only access (401/403), validation errors.
- **S3 Frontend: chart kit.** `src/components/charts/` (axis/scale helpers with unit tests, `AreaChart`, `BarChart`, `HBarList`, `Donut` or equivalent, `Sparkline`, `ChartTooltip`, `ChartFrame` with title/legend/table view), accessible and reduced-motion aware; palette validated with the dataviz script.
- **S4 Frontend: overview page.** Route/nav, API hook with ignore flags, toolbar, KPI cards, charts composition, low-stock table, "Giá vốn" field in the product editor (payload `costPrice`, empty = null), tests for pure helpers (preset ranges, deltas, formatting), Playwright screenshots at 1280 and 390.

## 2. Part B - GHTK shipping fee

### 2.1 External API (verified from GHTK docs, 2026-10-01)
`GET {GHTK_BASE_URL}/services/shipment/fee` (production base `https://services.giaohangtietkiem.vn`; staging `https://services-staging.ghtklab.com`), headers `Token` and `X-Client-Source` (partner code). Query: `pick_province` (req), `pick_ward` (req), `pick_district`, `pick_address`, `pick_address_id`, `province` (req), `ward` (req), `district`, `address`, `weight` (grams, req), `value` (VND), `transport` (`road`|`fly`). Response: `{"success": true, "fee": {"name": "area1", "fee": 30400, "insurance_fee": 15000, "delivery": true, ...}}`; `fee.delivery=false` means GHTK does not deliver there. Fee used = `fee.fee` (insurance excluded). Province names must match GHTK's naming: keep a single mapping function (identity by default) so a mismatch discovered with a real token is a one-line fix.

### 2.2 Behavior
- Config (`ghtk.*`, env): `GHTK_TOKEN`, `GHTK_CLIENT_SOURCE`, `GHTK_BASE_URL` (default production), `GHTK_PICK_PROVINCE`, `GHTK_PICK_WARD`, optional `GHTK_PICK_DISTRICT`, `GHTK_PICK_ADDRESS`, `GHTK_TRANSPORT` (default `road`). GHTK is **enabled only when token, client source, pick province and pick ward are all non-blank**; otherwise every quote uses the table. Blank config never blocks startup.
- `ShippingQuoteService.quote(user cart, province, ward, address)`: computes `weightGrams` (sum of quantity x `Product.effectiveWeight()`; default 300 g when null) and `value` (cart subtotal, server-side); calls GHTK when enabled; **falls back** to `ShippingService` table fee when GHTK is disabled, errors/times out (5 s connect, 10 s read), returns `success=false`, or `delivery=false`. Result: `{fee, source: "GHTK"|"TABLE", estimated (true for TABLE), weightGrams, deliverable (false only when GHTK says no and the table has no entry), message}`. Cache GHTK results in memory for 10 minutes keyed by (province, ward, address-normalized, weight, value bucket) so repeated quotes and the checkout recompute are stable and cheap.
- `POST /shipping/quote` (authenticated, body `{province, ward, address}`) returns the quote for the **user's current cart**.
- **Checkout**: `CheckoutRequest` gains required `ward` (<= 100 chars). The server **recomputes the shipping fee** with the same quote service at order creation (never trusts a client fee), and stores `Order.ward`, `Order.shippingSource` (GHTK|TABLE), `Order.weightGrams`. `Order.ward` nullable for old orders. Responses (`OrderResponse`) add `ward`, `shippingSource`, `weightGrams`; the confirmation email shows the ward.
- `Product.weightGrams` (`Integer`, nullable, 1..50000), editable in the admin product form ("Cân nặng (g)"); public product JSON does not need it.

### 2.3 Frontend
- Checkout: new required **Phường/Xã** field next to the province select; after province/ward/address settle (debounced 600 ms) call `POST /shipping/quote`; summary shows the fee with a source badge ("Phí GHTK" or "Phí tạm tính"), animated change, skeleton while loading, and a graceful inline note when the quote failed (fallback fee still shown). Submit uses the server-computed fee (the client sends no fee). Validation messages in Vietnamese; all previous checkout behavior preserved.
- Admin: weight field in the product editor; order list/detail show ward and shipping source; shipping-rates admin page text notes that the table is the fallback when GHTK is unavailable.

### 2.4 Tasks
- **G1 Backend: data.** `Product.weightGrams` (+admin request/response, validation), `Order.ward/shippingSource/weightGrams`, `CheckoutRequest.ward`, `OrderResponse` fields, email shows ward, tests.
- **G2 Backend: GHTK client and quote service.** `GhtkProperties`, `GhtkClient` (RestClient, headers, timeouts), province-name mapping function, `ShippingQuoteService` with fallback and 10-minute cache, `POST /shipping/quote`, tests with `MockRestServiceServer` (success, `delivery=false`, `success=false`, HTTP 500, timeout, disabled config, cache hit, weight/value computation, unknown province in the table -> `deliverable=false`), `.env.example` additions.
- **G3 Backend: checkout integration.** `OrderService.checkout` uses `ShippingQuoteService` (fee, source, weight, ward stored), keeps atomic stock logic untouched, tests (GHTK fee applied, fallback applied, client cannot influence fee, order stores source/weight/ward).
- **G4 Frontend.** Checkout ward field + quote UI, admin weight field, order views, pure-helper tests, Playwright verification with mocked quote responses (GHTK, fallback, loading, error).

## 3. Out of scope
Creating GHTK shipments, tracking, COD reconciliation, district/ward pick lists or address autocomplete, GHTK webhook, multiple warehouses, per-variant weight, profit after payment-gateway fees, data export, scheduled reports, real-time dashboards.

## 4. Verification and conventions
Same conventions as the shop (ApiResponse, AppException/ErrorCode, records, `Test` suffix, H2 test profile, `mvn -q clean test`, `npm test`/`npm run build`, ignore flags in effects, UI from the redesign kit, Vietnamese copy). Money/stock/payment code is not touched except the checkout shipping step (G3). Playwright via the installed Edge. Commit trailer `Co-Authored-By: Claude ... <noreply@anthropic.com>`; push after each finished piece (secret scan first).
