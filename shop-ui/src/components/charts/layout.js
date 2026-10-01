/** Pure layout math shared by AreaChart and BarChart (margins, scales, label thinning, bar shapes). */
import { bandScale, estimateTextWidth, extent, linearScale, maxLabelsFor, niceTicks, thinIndices } from './scales.js';

export const AXIS_FONT = 12;
export const MAX_BAR = 24;
const MOBILE = 600;

/**
 * Computes everything a cartesian chart needs to draw. `xLabels` are the formatted x labels (one per datum),
 * `values` every plotted y value (nulls ignored). `band` true lays the x axis out as bars (band centers), false as
 * points spread edge to edge. `endLabel` reserves top room for a label above a bar; `endLabelText` reserves right
 * room for a label beside the last point. Returns margins, plot box, ticks, y scale, x positions, labelled indices, bar width.
 */
export function cartesianLayout({ width, height, values, xLabels, formatY = String, integer = false, band = false, endLabel = false, endLabelText = '' }) {
  const n = xLabels.length;
  const { min, max } = extent(values);
  const allZero = min === 0 && max === 0;
  const top = endLabel ? 22 : 14;
  const bottom = 30;
  const plotH = Math.max(40, height - top - bottom);
  const ticks = niceTicks(Math.min(0, min), Math.max(0, max), Math.max(2, Math.round(plotH / 52)), { integer });
  const tickLabels = ticks.map((t) => (allZero && t !== 0 ? '' : formatY(t)));
  const left = Math.ceil(Math.max(...tickLabels.map((l) => estimateTextWidth(l, AXIS_FONT)), 8)) + 14;
  // A right-hand direct label (last value of a single line) reserves its width so it never sits on the line.
  const right = band ? 4 : endLabelText ? Math.ceil(estimateTextWidth(endLabelText, AXIS_FONT)) + 18 : 10;
  const plotW = Math.max(20, width - left - right);
  const y = linearScale([ticks[0], ticks[ticks.length - 1]], [top + plotH, top]);

  let xs;
  let bandwidth = 0;
  if (band) {
    const paddingOuter = n < 8 ? 0.3 : 0.1;
    // 2px surface gap between bars; very dense bars (step < ~7px) shrink the gap so a bar never thins to a hairline.
    const step = n > 0 ? plotW / (n + paddingOuter * 2) : 0;
    const b = bandScale(n, [left, left + plotW], { gap: Math.min(2, step * 0.3), paddingOuter });
    xs = Array.from({ length: n }, (_, i) => b.center(i));
    bandwidth = Math.min(MAX_BAR, b.bandwidth);
  } else {
    xs = Array.from({ length: n }, (_, i) => (n <= 1 ? left + plotW / 2 : left + (i * plotW) / (n - 1)));
  }

  const labelW = Math.max(16, ...xLabels.map((l) => estimateTextWidth(l, AXIS_FONT)));
  const cap = width < MOBILE ? 6 : 12;
  const labelIdx = thinIndices(n, maxLabelsFor(plotW, labelW, { gap: 18, cap }));

  return {
    width, height, top, bottom, left, right, plotW, plotH, ticks, tickLabels, y, xs, bandwidth, labelIdx, labelW, allZero,
    baselineY: y(Math.max(ticks[0], Math.min(0, ticks[ticks.length - 1]))),
  };
}

/** Center x of a text label of width `w` kept inside [0, width]. */
export function clampLabelX(x, w, width) {
  if (width <= w) return width / 2;
  return Math.min(width - w / 2, Math.max(w / 2, x));
}

/**
 * SVG path of a bar from `baseY` to `valueY` (pixels), `w` wide, centered on `cx`, with the data end rounded
 * (radius <= 4, <= half the width and <= the height) and the baseline end square. Zero height -> ''.
 */
export function barPath(cx, w, baseY, valueY, radius = 4) {
  const h = Math.abs(baseY - valueY);
  if (!(h > 0) || !(w > 0)) return '';
  const r = Math.min(radius, w / 2, h);
  const x0 = cx - w / 2;
  const x1 = cx + w / 2;
  const f = (v) => Math.round(v * 100) / 100;
  if (valueY < baseY) {
    return `M${f(x0)},${f(baseY)}V${f(valueY + r)}A${f(r)},${f(r)} 0 0 1 ${f(x0 + r)},${f(valueY)}H${f(x1 - r)}A${f(r)},${f(r)} 0 0 1 ${f(x1)},${f(valueY + r)}V${f(baseY)}Z`;
  }
  return `M${f(x0)},${f(baseY)}V${f(valueY - r)}A${f(r)},${f(r)} 0 0 0 ${f(x0 + r)},${f(valueY)}H${f(x1 - r)}A${f(r)},${f(r)} 0 0 0 ${f(x1)},${f(valueY - r)}V${f(baseY)}Z`;
}

/** Index of the last datum whose value is a finite number (-1 when none). */
export function lastFiniteIndex(values) {
  for (let i = (values?.length ?? 0) - 1; i >= 0; i -= 1) if (Number.isFinite(values[i])) return i;
  return -1;
}

/** Index of the largest finite value (first one on ties; -1 when none or all <= 0). */
export function maxIndex(values) {
  let best = -1;
  for (let i = 0; i < (values?.length ?? 0); i += 1) {
    const v = values[i];
    if (Number.isFinite(v) && v > 0 && (best < 0 || v > values[best])) best = i;
  }
  return best;
}

/** Bar length 0..1 of `value` against the largest positive value; negatives, zeros and gaps are 0. */
export function barShare(value, max) {
  if (!Number.isFinite(value) || value <= 0 || !Number.isFinite(max) || max <= 0) return 0;
  return Math.min(1, value / max);
}

/** Total and shares 0..1 of each part (negative or non-finite parts count as 0; a zero total gives all 0). */
export function partShares(values) {
  const clean = (values || []).map((v) => (Number.isFinite(v) && v > 0 ? v : 0));
  const total = clean.reduce((a, b) => a + b, 0);
  return { total, shares: clean.map((v) => (total > 0 ? v / total : 0)) };
}
