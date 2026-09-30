/** Clamps a quantity to [min, max] as an integer, treating anything non-numeric as `min`. */
export function clampQuantity(value, min = 1, max = 99) {
  if (value === '' || value === null || value === undefined) return min;
  const n = Math.floor(Number(value));
  if (!Number.isFinite(n)) return min;
  return Math.min(max, Math.max(min, n));
}
