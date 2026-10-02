# Carrier choice at checkout (GHN / GHTK)

Date: 2026-10-02. Extends `2026-10-01-ghn-shipping-design.md` and `2026-10-01-stats-and-ghtk-design.md`.

## Problem

When both GHN and GHTK are enabled the quote service runs a fixed priority chain (GHN, then GHTK, then the
fixed province table). The customer sees one fee from one carrier, cannot compare, and cannot pick the carrier
they prefer. GHTK is only asked when GHN fails or refuses.

## Goal

When both carriers can quote the address, the checkout lists both fees, pre-selects the cheaper one and lets the
customer pick the other. The order stores the carrier the customer chose and its fee.

## Rules

| Situation | Behaviour |
|---|---|
| Both carriers quote | Two options. Default = cheaper; equal fee -> GHN. Customer may switch. |
| One carrier quotes | One option, shown as today, nothing to choose. |
| No carrier quotes (down, breaker open, refused, not configured) | Fixed per-province table (admin `/admin/shipping-rates`, set manually), `source: TABLE`, `estimated: true`, labelled "Phí tạm tính". Not an option list, nothing to choose. Province missing from the table -> not deliverable, as today. |
| Customer's chosen carrier no longer quotes at order time | `SHIPPING_CARRIER_UNAVAILABLE` (409). UI shows the message and re-quotes. The server never silently switches carrier, because the price would change. |
| Order without `carrier` (old clients) | Cheapest option, same as the default. |

Behaviour change: GHN no longer wins automatically; the default is the cheaper carrier.

## API

`POST /shipping/quote` (authenticated, body unchanged). The response keeps its current top-level fields
(`fee, source, estimated, weightGrams, deliverable, message`), which describe the default selection, and adds:

```json
"options": [
  { "source": "GHN",  "fee": 38500, "estimated": false },
  { "source": "GHTK", "fee": 32000, "estimated": false }
]
```

`options` lists only live carriers (never `TABLE`), cheapest first. It is empty when the fee is the table fee.

`POST /orders` (`CheckoutRequest`) gains optional `carrier`: `GHN` or `GHTK` (any other value is `INVALID_INPUT`).
The server recomputes the quote for that carrier from database data; the fee is never taken from the client.

No database change: `orders.shipping_source` already stores `GHN | GHTK | TABLE`.

## Backend design (`ShippingQuoteService`)

- Extract `ghtkQuote(...)` from `quoteResolved` (mirrors the existing `ghnQuote`): returns the quote or null when
  GHTK cannot answer. Cache and circuit breaker stay per carrier.
- New `quoteOptions(measure, address)`: asks GHN (only with GHN ids) and GHTK (only when enabled and the address has
  text names; after `resolveAddress` GHN-id addresses carry names from GHN master data) in parallel so waits do not
  add up; returns the non-null quotes sorted cheapest first (tie: GHN first).
- `quoteResolved(measure, address)` = first option, else the table fallback (existing `tableQuote` and messages).
- New `quoteForCarrier(measure, address, carrier)`: the option for that carrier, else
  `SHIPPING_CARRIER_UNAVAILABLE`; a null carrier means `quoteResolved`. Never throws through the transactional proxy
  in a way that marks the outer transaction rollback-only (same rule as today's `tableQuote`).
- `OrderService.checkout` calls `quoteForCarrier(..., request.carrier())`.
- New `ErrorCode.SHIPPING_CARRIER_UNAVAILABLE` (409) in the shipping 20xx range, Vietnamese message.

## Frontend (checkout)

- `utils/shipping.js`: helpers for `options` (default selection, keep the user's choice across re-quotes while it is
  still offered, else fall back to the cheapest).
- Checkout "Phí vận chuyển" block: with 2 options render a radio list (carrier label, fee, "Rẻ nhất" on the cheapest);
  the total follows the selected option. With 0 or 1 option render as today.
- `createOrder` payload carries `carrier` only when the customer saw 2 options.
- On `SHIPPING_CARRIER_UNAVAILABLE`: danger toast, re-quote, selection falls back to the cheapest.

## Admin: turn each carrier on or off

The "Đơn vị vận chuyển" card on `/admin/settings` gets a switch per carrier so the admin can choose which carriers
the shop uses, without touching `.env` or restarting.

- **Effective availability** = configured (credentials in `.env`, as today) AND switched on. Only an available
  carrier is asked for a quote. Both unavailable -> the fixed province table (same rule as "no carrier quotes").
- **Card states per carrier:** not configured (switch disabled, hint to add the env vars, as today); configured and on
  ("Đang bật"); configured and off ("Đã tắt"). When nothing is available the card says the fixed table is in use.
- **Default on.** A configured carrier with no stored choice is on, so nothing changes until the admin turns one off.
- **Storage:** two columns on the single `shop_settings` row, `ghn_enabled` and `ghtk_enabled` (BOOLEAN, null or true =
  on). Needs `migration_v5_carrier_toggle.sql` (backup first, `DEFAULT TRUE`, existing row backfilled) per the
  database workflow in CLAUDE.md.
- **API:** `PUT /admin/settings/carriers` (ADMIN) with `{ghn: boolean, ghtk: boolean}`; separate from the shop form
  `PUT /admin/settings/shop` so toggling never requires a valid pickup address or a GHN round trip. The switch saves
  on click (optimistic, reverts with a toast on failure). `GET /admin/settings/shop` carriers become
  `{ghn: {configured, enabled, shopId}, ghtk: {configured, enabled}}` (`configured` is today's `enabled`).
  Updates `updatedAt/updatedBy`.
- **Backend:** `ShopSettingsService` keeps the switches in its in-memory snapshot next to the pickup and exposes
  `carrierSwitches()`; a change clears the quote cache through the existing change listener, so it applies to the next
  quote immediately. `ShippingQuoteService` treats GHN as available when `ghnProps.isEnabled() && switch.ghn`, and GHTK
  likewise. The switch only controls **fee quoting**: GHN address master data (the province/district/ward selects) and
  `addressMode` stay tied to GHN credentials, so turning GHN off while GHTK is on keeps the same address form and GHTK
  is quoted with the resolved names.
- **Checkout config:** `GET /shipping/config` `provider` reports the first available quoting carrier (`GHN`, `GHTK`
  or `TABLE`).
- **In-flight orders:** a customer who picked a carrier that the admin then turned off gets
  `SHIPPING_CARRIER_UNAVAILABLE` (existing rule), re-quotes and sees the remaining options.

## Out of scope

Ranking carriers, per-carrier service types, MoMo, order-detail changes (the stored `shippingSource` label already
renders).

## Testing

- Service: both quote (cheaper wins, tie -> GHN), one down, one refused, none (table), GHN-ids address,
  `quoteForCarrier` with a carrier that is available / not available / null.
- Order: checkout with `carrier`, without it, with an unavailable one (nothing saved, stock untouched).
- Controller: `options` shape, `carrier` validation.
- Frontend: option/default/keep-selection helpers (Vitest), carrier switch states and optimistic revert.
- Switches: GHN off -> only GHTK quoted, both off -> table, switch change clears cached quotes, endpoint is
  ADMIN-only, default (no stored value) is on, a carrier without credentials is never available even when on.
