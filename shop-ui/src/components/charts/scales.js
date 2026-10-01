/**
 * Pure scale, path and formatting helpers for the SVG chart kit (no React, no DOM).
 *
 * Number formats (Vietnamese conventions, decimal comma, dot grouping):
 *  - formatVndCompact (axes, direct labels): units "N" (nghìn, 1e3), "tr" (triệu, 1e6), "tỷ" (1e9), at most two
 *    decimals, no currency sign (the chart title/subtitle says "₫"): 450000 -> "450 N", 1200000 -> "1,2 tr",
 *    1250000000 -> "1,25 tỷ", 999 -> "999". A value that rounds up to 1000 of a unit moves to the next unit.
 *  - formatVndFull (tooltips, tables): the shop's formatVnd ("1.200.000 ₫").
 *  - formatPercent: ratio 0..1 -> "12,5%".
 */
import { formatVnd } from '../../utils/money.js';

const isNum = (v) => typeof v === 'number' && Number.isFinite(v);

/** Rounds away binary noise (0.1 + 0.2) to the precision of `step`. */
function roundTo(value, step) {
  const decimals = Math.max(0, -Math.floor(Math.log10(Math.abs(step))) + 1);
  return Number(value.toFixed(Math.min(20, decimals)));
}

/** A "nice" step (1, 2, 2.5, 5 x 10^k) close to `rough`. */
export function niceStep(rough) {
  if (!isNum(rough) || rough <= 0) return 1;
  const power = 10 ** Math.floor(Math.log10(rough));
  const f = rough / power;
  const nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
  return nice * power;
}

/**
 * Round tick values covering [min, max] with about `count` intervals. The first tick is <= min and the last >= max.
 * A degenerate domain is widened: [0, 0] -> [0, 1]; [v, v] -> includes 0 (so a single value still has a baseline).
 * Non-finite bounds are treated as 0; min > max is swapped. `integer: true` (counts) never steps below 1.
 */
export function niceTicks(min, max, count = 5, { integer = false } = {}) {
  let lo = isNum(min) ? min : 0;
  let hi = isNum(max) ? max : 0;
  if (lo > hi) [lo, hi] = [hi, lo];
  if (lo === hi) {
    if (lo === 0) hi = 1;
    else if (lo > 0) lo = 0;
    else hi = 0;
  }
  const n = Math.max(1, Math.round(isNum(count) ? count : 5));
  let step = niceStep((hi - lo) / n);
  if (integer) step = Math.max(1, Math.round(step === 2.5 ? 2 : step));
  const start = Math.floor(roundTo(lo / step, 1e-9)) * step;
  const end = Math.ceil(roundTo(hi / step, 1e-9)) * step;
  const ticks = [];
  for (let i = 0, v = start; v <= end + step / 2 && i < 1000; i += 1, v = start + i * step) {
    ticks.push(roundTo(v, step) || 0);
  }
  return ticks;
}

/** Linear map from `domain` [d0, d1] to `range` [r0, r1]; `.invert(r)` maps back. A zero-width domain maps to the range midpoint. */
export function linearScale([d0, d1], [r0, r1]) {
  const span = d1 - d0;
  const scale = (v) => (span === 0 ? (r0 + r1) / 2 : r0 + ((v - d0) / span) * (r1 - r0));
  scale.invert = (r) => (r1 === r0 ? d0 : d0 + ((r - r0) / (r1 - r0)) * span);
  scale.domain = [d0, d1];
  scale.range = [r0, r1];
  return scale;
}

/**
 * Evenly divided bands for `count` items across `range`. `paddingOuter` is a fraction of a step on each end.
 * Returns { step, bandwidth (= step - gap, never < 1), start(i), center(i) }. `gap` is the px space between bands.
 */
export function bandScale(count, [r0, r1], { gap = 2, paddingOuter = 0 } = {}) {
  const n = Math.max(0, count | 0);
  const width = r1 - r0;
  const step = n > 0 ? width / (n + paddingOuter * 2) : 0;
  const bandwidth = n > 0 ? Math.max(1, step - gap) : 0;
  const start = (i) => r0 + step * (i + paddingOuter) + (step - bandwidth) / 2;
  return { step, bandwidth, start, center: (i) => start(i) + bandwidth / 2 };
}

/** Splits points into runs of consecutive finite {x, y} (null/NaN y breaks the line). */
function segmentsOf(points) {
  const runs = [];
  let run = [];
  for (const p of points || []) {
    if (p && isNum(p.x) && isNum(p.y)) run.push(p);
    else if (run.length) { runs.push(run); run = []; }
  }
  if (run.length) runs.push(run);
  return runs;
}

const fmt = (n) => Math.round(n * 100) / 100;

/** Monotone cubic tangents (Fritsch-Carlson, as d3.curveMonotoneX): no overshoot, so non-negative data stays >= 0. */
function monotoneTangents(pts) {
  const n = pts.length;
  const d = [];
  for (let i = 0; i < n - 1; i += 1) {
    const h = pts[i + 1].x - pts[i].x;
    d.push(h === 0 ? 0 : (pts[i + 1].y - pts[i].y) / h);
  }
  const m = new Array(n).fill(0);
  for (let i = 1; i < n - 1; i += 1) {
    if (d[i - 1] * d[i] <= 0) { m[i] = 0; continue; }
    const h0 = pts[i].x - pts[i - 1].x;
    const h1 = pts[i + 1].x - pts[i].x;
    const p = (d[i - 1] * h1 + d[i] * h0) / (h0 + h1);
    m[i] = Math.sign(d[i]) * Math.min(Math.abs(d[i - 1]), Math.abs(d[i]), 0.5 * Math.abs(p)) * 2;
  }
  if (n > 1) {
    m[0] = n > 2 ? (3 * d[0] - m[1]) / 2 : d[0];
    m[n - 1] = n > 2 ? (3 * d[n - 2] - m[n - 2]) / 2 : d[n - 2];
    // Endpoint tangents must keep the sign of their secant, or the curve overshoots at the edges.
    if (m[0] * d[0] <= 0) m[0] = 0;
    if (m[n - 1] * d[n - 2] <= 0) m[n - 1] = 0;
    if (Math.abs(m[0]) > 3 * Math.abs(d[0])) m[0] = 3 * d[0];
    if (Math.abs(m[n - 1]) > 3 * Math.abs(d[n - 2])) m[n - 1] = 3 * d[n - 2];
  }
  return m;
}

function runPath(run, curve) {
  let path = `M${fmt(run[0].x)},${fmt(run[0].y)}`;
  if (run.length === 1) return path;
  if (curve !== 'monotone' || run.length === 2) {
    for (let i = 1; i < run.length; i += 1) path += `L${fmt(run[i].x)},${fmt(run[i].y)}`;
    return path;
  }
  const m = monotoneTangents(run);
  for (let i = 0; i < run.length - 1; i += 1) {
    const a = run[i];
    const b = run[i + 1];
    const dx = (b.x - a.x) / 3;
    path += `C${fmt(a.x + dx)},${fmt(a.y + dx * m[i])},${fmt(b.x - dx)},${fmt(b.y - dx * m[i + 1])},${fmt(b.x)},${fmt(b.y)}`;
  }
  return path;
}

/**
 * SVG path strings for a line and its area down to `baseline` (pixel y). Points are {x, y} in pixels; a null y
 * leaves a gap. `curve`: 'linear' | 'monotone'. Runs of one point produce a moveto only (draw a marker instead)
 * and no area. Empty input -> { line: '', area: '' }.
 */
export function pathFromPoints(points, { curve = 'linear', baseline = 0 } = {}) {
  const runs = segmentsOf(points);
  const line = runs.map((run) => runPath(run, curve)).join('');
  const area = runs
    .filter((run) => run.length > 1)
    .map((run) => {
      const first = run[0];
      const last = run[run.length - 1];
      return `${runPath(run, curve)}L${fmt(last.x)},${fmt(baseline)}L${fmt(first.x)},${fmt(baseline)}Z`;
    })
    .join('');
  return { line, area };
}

/** Index of the value in ascending `xs` closest to `x` (ties go left); -1 for an empty list. */
export function nearestIndexByX(xs, x) {
  if (!xs || xs.length === 0 || !isNum(x)) return xs && xs.length ? 0 : -1;
  let lo = 0;
  let hi = xs.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (xs[mid] <= x) lo = mid; else hi = mid;
  }
  return Math.abs(xs[hi] - x) < Math.abs(x - xs[lo]) ? hi : lo;
}

/**
 * Which of `count` evenly spaced x labels to draw so at most `maxLabels` show (>= 1). The last index (the most
 * recent bucket) is always kept and the others step back from it, so labels never collide at the right edge.
 */
export function thinIndices(count, maxLabels) {
  const n = Math.max(0, count | 0);
  if (n === 0) return [];
  const cap = Math.max(1, Math.floor(maxLabels) || 1);
  const step = Math.ceil(n / cap);
  const out = [];
  for (let i = n - 1; i >= 0; i -= step) out.unshift(i);
  return out;
}

/** How many labels of about `labelWidth` px fit in `plotWidth` px with `gap` px between them (>= 1, <= `cap`). */
export function maxLabelsFor(plotWidth, labelWidth, { gap = 16, cap = 12 } = {}) {
  if (!isNum(plotWidth) || plotWidth <= 0) return 1;
  const fits = Math.floor((plotWidth + gap) / (Math.max(1, labelWidth) + gap));
  return Math.max(1, Math.min(cap, fits));
}

/** Approximate rendered width of `text` in Inter at `fontSize` px with tabular figures (no DOM needed). */
export function estimateTextWidth(text, fontSize = 12) {
  return String(text ?? '').length * fontSize * 0.6;
}

const viNumber = (digits) => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: digits });
const NUM0 = viNumber(0);
const NUM2 = viNumber(2);
const UNITS = [
  { size: 1e9, suffix: 'tỷ' },
  { size: 1e6, suffix: 'tr' },
  { size: 1e3, suffix: 'N' },
];

/** Compact VND for axes and direct labels: "450 N", "1,2 tr", "3 tỷ" (see the file header). */
export function formatVndCompact(value) {
  if (!isNum(value)) return '0';
  const sign = value < 0 ? '-' : '';
  const abs = Math.abs(value);
  for (let u = 0; u < UNITS.length; u += 1) {
    const { size, suffix } = UNITS[u];
    if (abs < size) continue;
    const scaled = Math.round((abs / size) * 100) / 100;
    if (scaled >= 1000 && u > 0) {
      const up = UNITS[u - 1];
      return `${sign}${NUM2.format(Math.round((abs / up.size) * 100) / 100)} ${up.suffix}`;
    }
    return `${sign}${NUM2.format(scaled)} ${suffix}`;
  }
  const rounded = Math.round(abs);
  if (rounded >= 1000) return `${sign}1 N`;
  return `${sign}${NUM0.format(rounded)}`;
}

/** Full VND for tooltips and tables ("1.200.000 ₫"); null/NaN -> "—". */
export const formatVndFull = (value) => (isNum(value) ? formatVnd(value) : '—');

/** Whole number with Vietnamese grouping ("1.234"); null/NaN -> "—". */
export const formatCount = (value) => (isNum(value) ? NUM0.format(value) : '—');

/** Ratio (0..1) as a percent with up to `digits` decimals: 0.125 -> "12,5%". null/NaN -> "—". */
export function formatPercent(ratio, digits = 1) {
  if (!isNum(ratio)) return '—';
  return `${viNumber(digits).format(Math.round(ratio * 100 * 10 ** digits) / 10 ** digits)}%`;
}

/** Signed percent for deltas: 0.125 -> "+12,5%", -0.03 -> "−3%" (true minus sign), 0 -> "0%", null -> "—". */
export function formatSignedPercent(ratio, digits = 1) {
  if (!isNum(ratio)) return '—';
  const text = formatPercent(Math.abs(ratio), digits);
  if (text === '0%') return text;
  return `${ratio > 0 ? '+' : '−'}${text}`;
}

const BUCKET_RE = { day: /^(\d{4})-(\d{2})-(\d{2})$/, month: /^(\d{4})-(\d{2})$/, year: /^(\d{4})$/ };
const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

/** groupBy of a bucket string by its shape: "2026-09-05" day, "2026-09" month, "2026" year; null if unknown. */
export function bucketKind(bucket) {
  const s = String(bucket ?? '');
  return Object.keys(BUCKET_RE).find((k) => BUCKET_RE[k].test(s)) ?? null;
}

/** Short axis label: day "2026-09-05" -> "05/09", month "2026-09" -> "09/2026", year -> "2026". Unknown -> as is. */
export function formatBucket(bucket, groupBy = bucketKind(bucket)) {
  const s = String(bucket ?? '');
  const m = BUCKET_RE[groupBy]?.exec(s);
  if (!m) return s;
  if (groupBy === 'day') return `${m[3]}/${m[2]}`;
  if (groupBy === 'month') return `${m[2]}/${m[1]}`;
  return m[1];
}

/** Long label for tooltips and tables: "T7, 05/09/2026", "Tháng 09/2026", "Năm 2026". Unknown -> as is. */
export function formatBucketLong(bucket, groupBy = bucketKind(bucket)) {
  const s = String(bucket ?? '');
  const m = BUCKET_RE[groupBy]?.exec(s);
  if (!m) return s;
  if (groupBy === 'day') {
    const weekday = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]))).getUTCDay();
    return `${WEEKDAYS[weekday]}, ${m[3]}/${m[2]}/${m[1]}`;
  }
  if (groupBy === 'month') return `Tháng ${m[2]}/${m[1]}`;
  return `Năm ${m[1]}`;
}

/**
 * Change from `previous` to `current`: { abs, pct, direction }. pct is relative to |previous| and null when previous
 * is 0; direction is 'up' | 'down' | 'flat' (|abs| below 1e-9 counts as flat, so float noise in rates is flat).
 * Returns null when either value is missing (e.g. profit unknown).
 */
export function delta(current, previous) {
  if (!isNum(current) || !isNum(previous)) return null;
  const raw = current - previous;
  const abs = Math.abs(raw) < 1e-9 ? 0 : raw;
  const pct = previous === 0 ? null : abs / Math.abs(previous);
  return { abs, pct, direction: abs > 0 ? 'up' : abs < 0 ? 'down' : 'flat' };
}

/** Min and max of the finite numbers in `values` ({ min: 0, max: 0 } when there are none). */
export function extent(values) {
  let min = Infinity;
  let max = -Infinity;
  for (const v of values || []) {
    if (!isNum(v)) continue;
    if (v < min) min = v;
    if (v > max) max = v;
  }
  return min === Infinity ? { min: 0, max: 0 } : { min, max };
}
