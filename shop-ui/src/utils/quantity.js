/** Clamps a quantity to [min, max] as an integer, treating anything non-numeric as `min`. */
export function clampQuantity(value, min = 1, max = 99) {
  if (value === '' || value === null || value === undefined) return min;
  const n = Math.floor(Number(value));
  if (!Number.isFinite(n)) return min;
  return Math.min(max, Math.max(min, n));
}

/** Highest quantity a shopper may pick for a variant with `stock` units: stock, capped at `cap` (never negative). */
export function maxQuantityFor(stock, cap = 99) {
  const n = Math.floor(Number(stock));
  if (!Number.isFinite(n)) return 0;
  return Math.max(0, Math.min(n, cap));
}

/** Stock label level: "out" when nothing is left, "low" under `lowBelow` units, otherwise "ok". */
export function stockLevel(stock, lowBelow = 5) {
  if (!(stock > 0)) return 'out';
  return stock < lowBelow ? 'low' : 'ok';
}

/**
 * Value to commit for a typed/stepped `draft`, or null when nothing should change: the draft equals the current
 * value (e.g. tabbing through the field) or clamps back to it. Prevents silent updates on blur.
 */
export function clampCommit(draft, value, min = 1, max = 99) {
  if (String(draft).trim() === String(value)) return null;
  const clamped = clampQuantity(draft, min, max);
  return clamped === value ? null : clamped;
}
