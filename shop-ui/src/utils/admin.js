/** Pure helpers for the admin screens (filtering, numeric validation, variant merging). */

/** Lower-case, diacritic-free text for forgiving Vietnamese search ("Áo" matches "ao"). */
export const normalizeText = (s) => String(s ?? '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').trim();

/** Keeps the items whose picked fields contain every word of `query` (empty query keeps everything). */
export function filterByText(items, query, pick) {
  const words = normalizeText(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) return items;
  return items.filter((item) => {
    const haystack = normalizeText(pick(item).filter(Boolean).join(' '));
    return words.every((w) => haystack.includes(w));
  });
}

export const filterProducts = (items, query) => filterByText(items, query, (p) => [p.name, p.slug, p.categoryName]);
export const filterRates = (items, query) => filterByText(items, query, (r) => [r.province]);

/** Parses a whole number >= 0 from a string or number; returns null when empty/invalid. */
export function parseCount(value) {
  if (typeof value === 'number') return Number.isSafeInteger(value) && value >= 0 ? value : null;
  const s = String(value ?? '').trim();
  return /^\d{1,15}$/.test(s) ? Number(s) : null;
}

/** Validation message for a required non-negative integer field ('' when valid). */
export function countError(value, label) {
  if (String(value ?? '').trim() === '') return `${label} là bắt buộc`;
  return parseCount(value) === null ? `${label} phải là số nguyên không âm` : '';
}

/** Like countError but an empty value is allowed (variant price override: empty means "use base price"). */
export const optionalCountError = (value, label) => (String(value ?? '').trim() === '' ? '' : countError(value, label));

/** Errors of the variant fields that can be edited inline or added: { stock, price }. Empty object when valid. */
export function variantErrors({ stock, priceOverride }) {
  const errors = {};
  const stockMsg = countError(stock, 'Tồn kho');
  if (stockMsg) errors.stock = stockMsg;
  const priceMsg = optionalCountError(priceOverride, 'Giá riêng');
  if (priceMsg) errors.price = priceMsg;
  return errors;
}

/** Optional cost price (admin only): '' / null -> null (unknown), otherwise a non-negative integer (invalid -> null too; validate first). */
export const costPriceValue = (value) => (String(value ?? '').trim() === '' ? null : parseCount(value));

/** Validation message for the optional cost price field ('' when empty or valid). */
export const costPriceError = (value) => optionalCountError(value, 'Giá vốn');

/** VariantResponse.price is the effective price: it is an override only when it differs from the base price. */
export const overrideOf = (variant, basePrice) => (Number(variant.price) !== Number(basePrice) ? String(variant.price) : '');

/**
 * Merges the server's variants into the editor's local list without discarding unsaved edits:
 * variants already listed keep their local (possibly edited) values, except ids in `refreshIds`, which take the
 * server value; variants unknown locally are appended.
 */
export function mergeVariants(local, server, basePrice, refreshIds = []) {
  const refresh = new Set(refreshIds);
  const localById = new Map(local.map((v) => [v.id, v]));
  const merged = server.map((sv) => {
    const kept = localById.get(sv.id);
    return kept && !refresh.has(sv.id) ? kept : { ...sv, priceOverride: overrideOf(sv, basePrice) };
  });
  return merged;
}

/** Counts of items per key, e.g. { ALL: 3, ACTIVE: 2 }; used for filter chips. */
export const countBy = (items, keyOf) => items.reduce((acc, item) => {
  const k = keyOf(item);
  acc[k] = (acc[k] || 0) + 1;
  return acc;
}, {});

/** URL slug from a name: diacritics removed, lower-case, words joined with dashes. */
export const slugify = (s) => normalizeText(s).replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

const PRODUCT_KEYS = ['name', 'slug', 'description', 'categoryId', 'basePrice', 'costPrice', 'imageUrl', 'active'];
const str = (v) => (v === null || v === undefined ? '' : String(v));

/** The user-editable parts of an editor state (product fields + per-variant stock/override/active), as plain strings. */
export function editorSnapshot(editing) {
  const product = {};
  for (const key of PRODUCT_KEYS) {
    const value = key === 'categoryId' ? (editing.categoryId ?? editing.category?.id) : editing[key];
    product[key] = key === 'active' ? Boolean(value) : str(value);
  }
  const variants = {};
  for (const v of editing.variants || []) {
    variants[v.id] = { stock: str(v.stock), priceOverride: str(v.priceOverride), active: Boolean(v.active) };
  }
  return { product, variants };
}

/** True when `current` (an editor state) differs from `baseline` (an editorSnapshot). Variants unknown to the baseline are ignored. */
export function isEditorDirty(baseline, current) {
  if (!baseline || !current) return false;
  const now = editorSnapshot(current);
  if (PRODUCT_KEYS.some((k) => now.product[k] !== baseline.product[k])) return true;
  return Object.entries(now.variants).some(([id, v]) => {
    const base = baseline.variants[id];
    return base && (base.stock !== v.stock || base.priceOverride !== v.priceOverride || base.active !== v.active);
  });
}
