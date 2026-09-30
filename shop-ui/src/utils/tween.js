/** Ease-out cubic: fast start, gentle landing. `t` is clamped to [0, 1]. */
export function easeOutCubic(t) {
  const x = Math.min(1, Math.max(0, t));
  return 1 - (1 - x) ** 3;
}

/** Integer value `t` (0..1) of the way from `from` to `to` with ease-out; exactly `to` at t >= 1. */
export function tweenValue(from, to, t) {
  if (t >= 1) return to;
  return Math.round(from + (to - from) * easeOutCubic(t));
}
