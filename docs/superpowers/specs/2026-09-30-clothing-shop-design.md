# Clothing Shop (MoMo payment) - Design Spec

Date: 2026-09-30
Status: Draft for review

## 1. Goal and context

Build a complete online clothing shop as a **learning / demo project** (sandbox only, no real money), on top of the existing Spring Boot `identify-service` (JWT auth, RBAC, MySQL). Customers browse products, pick size/color, keep a cart, check out, and pay by **MoMo** (wallet, domestic ATM card, international card via the MoMo gateway) or **COD**. The existing login code is reviewed and fixed as part of this work.

Confirmed decisions:
- Purpose: demo / coursework, MoMo sandbox.
- Products: clothing with size and color variants.
- Payment gateway: MoMo only. COD kept (assumed, not contradicted).
- Frontend: React + Vite (new `shop-ui/`), replacing the existing static HTML pages. Requires Node 20 LTS (user upgrades from v16.18).
- Extra features in scope: shipping fee by province, order confirmation email. Out of scope: coupons, reviews, wishlist, image upload (image URLs only).
- MoMo result handling: **no tunnel**. Return redirect + server-side query API. IPN endpoint is implemented but optional (usable later via ngrok by changing one config value).

## 2. Login review: fixes in scope

| # | Issue | Fix |
|---|---|---|
| 1 | `ApplicationInitConfig` creates `admin` without roles (`.roles(...)` commented) | Seed roles `ADMIN`, `USER`; admin gets `ADMIN`. Password from env, not hardcoded `admin` |
| 2 | `createUser` assigns no role | New users get role `USER` |
| 3 | `updateUser` always encodes `request.getPassword()`; roles taken from request | Password optional (only re-encode if present); roles changeable by ADMIN only |
| 4 | `@PreAuthorize("#userId == authentication.name")` compares UUID with username | Compare against the current user's id (or resolve by username) |
| 5 | `authenticate` ignores `status`, `loginAttempts`, `lastLoginAt` | Reject non-ACTIVE users; update `lastLoginAt`; count failed attempts and temporarily lock after a threshold |
| 6 | Distinct `USER_NOT_EXISTED` on login | Return `UNAUTHENTICATED` for unknown user and wrong password alike |
| 7 | JWT signer key and DB password defaults in `application.yaml` | Remove defaults for secrets; load from `.env` (`EnvConfig` exists) |
| 8 | CORS lacks Vite origin | Add `http://localhost:5173` and `http://127.0.0.1:5173` |

Further review items found during implementation are recorded in the plan, not silently changed.

## 3. Data model

Money is stored as integer VND (`long`). Existing `ddl-auto: update` limitations apply: schema changes follow the CLAUDE.md database workflow (inspect, backup, migration SQL, verify no `Error executing DDL`).

- `Category(id, name, slug unique)`
- `Product(id, name, slug unique, description, category, basePrice, imageUrl, active)`
- `ProductVariant(id, product, size, color, sku unique, stock, price nullable)`; unique on (product, size, color); price falls back to `basePrice`
- `Cart(id, user unique)`, `CartItem(id, cart, variant, quantity)`; unique on (cart, variant)
- `Order(id, code unique, user, status, paymentMethod, paymentStatus, subtotal, shippingFee, total, receiverName, phone, address, province, note, createdAt, paidAt, expiresAt)`
- `OrderItem(id, order, variantId, productName, size, color, unitPrice, quantity)`: snapshot of purchase-time data
- `Payment(id, order, provider, requestId, providerOrderId, transId, amount, status, rawResponse, createdAt)`
- `ShippingRate(province unique, fee)`, seeded for all 63 provinces with a default fee; editable by admin

Order status: `PENDING_PAYMENT` (MoMo, unpaid), `PENDING_CONFIRM` (COD, or MoMo paid awaiting admin), `CONFIRMED`, `SHIPPING`, `COMPLETED`, `CANCELLED`. Payment status: `UNPAID`, `PAID`, `FAILED`, `EXPIRED`.

## 4. Backend API (context path `/identity`, `ApiResponse` wrapper)

Public (GET): `/products`, `/products/{slug}`, `/categories`, `/shipping/fee?province=`
Authenticated: `/cart` (get, add, update, remove, clear), `POST /orders`, `GET /orders`, `GET /orders/{code}`, `POST /orders/{code}/pay/momo`, `GET /payments/momo/return`
Public (called by MoMo): `POST /payments/momo/ipn`, authenticated by HMAC-SHA256 signature
Admin (`hasRole('ADMIN')`): `/admin/products`, `/admin/variants`, `/admin/orders` (list, change status), `/admin/shipping-rates`

Layering follows the existing `controller -> service -> repository -> entity` structure, MapStruct mappers, `AppException` with new `ErrorCode` entries (out of stock, order not found, invalid payment signature, amount mismatch, etc.).

## 5. Checkout and MoMo payment flow

1. Client submits address, province, payment method. Server recomputes prices from the DB (never trusts client prices), adds the province shipping fee, and **decrements variant stock in the same transaction**, failing with an out-of-stock error if insufficient (row-level locking or conditional update to avoid overselling).
2. COD: order created as `PENDING_CONFIRM`, cart cleared, confirmation email sent.
3. MoMo: order created as `PENDING_PAYMENT` with `expiresAt = now + 15 min`. Server builds and signs (HMAC-SHA256, per MoMo v2 spec) a create request, calls MoMo `/v2/gateway/api/create`, stores a `Payment`, and returns `payUrl`. Client redirects to it. The `requestType` used for wallet/ATM/international card is set per the sandbox docs during implementation.
4. After payment MoMo redirects the browser to `/payments/momo/return`. The server then calls MoMo `/v2/gateway/api/query`, verifies the response signature, checks `orderId` and `amount` against the stored order, and finalizes.
5. Finalization is a single idempotent method shared by return handler and IPN: only `PENDING_PAYMENT -> PENDING_CONFIRM/PAID` once, safe to call repeatedly or concurrently.
6. Failure or timeout: a scheduled job cancels orders past `expiresAt` and restores stock; a failed result cancels and restores immediately.
7. After a successful state change (COD created, or MoMo `PAID`), send the confirmation email asynchronously (`@Async`, Spring Mail). Send failure is logged and never affects the order.

## 6. Frontend (`shop-ui/`)

React 18 + Vite + React Router. Pages: Home/product list (category filter, search), Product detail (size/color), Cart, Checkout, Payment result, My orders, Login, Signup, Admin (products/variants, orders, shipping rates). One `apiClient` attaches the JWT and refreshes on 401 using the existing `/auth/refresh`. `AuthContext` and `CartContext` hold state; routes are guarded by role. The old `login.html`, `signup.html`, `dashboard.html` are superseded by the React pages.

## 7. Configuration and secrets

All secrets from `.env` (never committed): `JWT_SIGNER_KEY`, DB credentials, `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `MOMO_ENDPOINT`, `MOMO_RETURN_URL`, `MOMO_IPN_URL`, mail SMTP settings. The user must supply MoMo sandbox keys and SMTP (Mailtrap or Gmail app password) at implementation time.

## 8. Testing

- Unit: price and shipping calculation, MoMo signature build/verify, order state transitions.
- Integration: create-order transaction (stock decrement, out-of-stock rejection), idempotent finalization (duplicate return/IPN), expiry job restoring stock.
- MoMo HTTP calls mocked in tests; a manual end-to-end run against the sandbox once keys are available.
- Frontend: smoke test of the main purchase path in a browser.

## 9. Out of scope

Coupons, reviews, wishlist, image upload, real-money operation, refunds, multi-currency, other payment gateways, tunnel-based IPN setup (supported by config only).

## 10. Risks and open items

- Node must be upgraded to 20 LTS before frontend work.
- MoMo sandbox keys and SMTP credentials needed from the user.
- Existing tables (`user`, etc.) may need migration SQL; existing rows must be backfilled per the DB workflow.
- The repository has many uncommitted changes from earlier work; implementation should happen on a fresh branch or worktree.

## 11. Amendments made during planning (2026-09-30)

- Shipping table is seeded with the 34 provincial-level units in force since July 2025 (not 63); admins can add rows.
- `Order` also stores the customer `email` captured at checkout (used for the confirmation email).
- A failed or cancelled MoMo attempt leaves the order `PENDING_PAYMENT` so the customer can retry until `expiresAt`; the expiry job cancels it afterwards (replaces "a failed result cancels immediately").
- `GET /payments/momo/return?orderCode=` ignores MoMo's redirect parameters and queries MoMo instead.
- The expiry job reconciles with MoMo before cancelling, because without a public IPN a customer who paid and closed the tab would otherwise be cancelled.
- Signup also fixed: client-supplied `id` no longer overwrites existing users; new users get role USER; accounts lock for 15 minutes after 5 failed logins.
- Frontend refreshes the JWT proactively before expiry (the backend refresh endpoint rejects expired tokens), rather than on 401.
