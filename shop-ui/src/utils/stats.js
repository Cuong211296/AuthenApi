/**
 * Pure helpers of the admin "Tổng quan" page: date ranges in the shop's time zone, group-by rules that mirror the
 * server validation, KPI card config and delta view models, and adapters from the stats API JSON to chart/table data.
 */
import { delta, formatBucket, formatBucketLong, formatCount, formatPercent, formatSignedPercent, formatVndFull } from '../components/charts/scales.js';
import { ORDER_STATUS_LABEL } from './labels.js';

export const VN_TZ = 'Asia/Ho_Chi_Minh';
export const MAX_RANGE_DAYS = 1100;
export const MAX_DAY_GROUP_DAYS = 366;
const DAY_MS = 86400000;
const ISO_RE = /^(\d{4})-(\d{2})-(\d{2})$/;

const zoneParts = new Intl.DateTimeFormat('en-US', { timeZone: VN_TZ, year: 'numeric', month: '2-digit', day: '2-digit' });

/** Today's date (YYYY-MM-DD) in Vietnam, whatever the browser's own zone is. */
export function vnToday(now = new Date()) {
  const p = Object.fromEntries(zoneParts.formatToParts(now).map((x) => [x.type, x.value]));
  return `${p.year}-${p.month}-${p.day}`;
}

function toUtcMs(iso) {
  const m = ISO_RE.exec(String(iso ?? ''));
  if (!m) return NaN;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const ms = Date.UTC(y, mo - 1, d);
  const back = new Date(ms);
  // Round trip rejects 2026-02-30 style dates that Date.UTC silently rolls over.
  return back.getUTCFullYear() === y && back.getUTCMonth() === mo - 1 && back.getUTCDate() === d ? ms : NaN;
}

export const isValidIsoDate = (iso) => Number.isFinite(toUtcMs(iso));

/** iso + n days (n may be negative), as YYYY-MM-DD. */
export function addDays(iso, n) {
  return new Date(toUtcMs(iso) + n * DAY_MS).toISOString().slice(0, 10);
}

/** Number of days in the inclusive range [from, to] (NaN when a date is invalid). */
export const rangeDays = (from, to) => Math.round((toUtcMs(to) - toUtcMs(from)) / DAY_MS) + 1;

export const PRESETS = [
  { key: 'today', label: 'Hôm nay' },
  { key: '7d', label: '7 ngày' },
  { key: '30d', label: '30 ngày' },
  { key: 'month', label: 'Tháng này' },
  { key: 'year', label: 'Năm nay' },
];

/** { from, to } of a preset, ending today in Vietnam. Unknown key -> the 30 day range. */
export function presetRange(key, now = new Date()) {
  const to = vnToday(now);
  switch (key) {
    case 'today': return { from: to, to };
    case '7d': return { from: addDays(to, -6), to };
    case 'month': return { from: `${to.slice(0, 8)}01`, to };
    case 'year': return { from: `${to.slice(0, 5)}01-01`, to };
    default: return { from: addDays(to, -29), to };
  }
}

/** Key of the preset equal to [from, to], or 'custom'. */
export function detectPreset(from, to, now = new Date()) {
  const hit = PRESETS.find((p) => {
    const r = presetRange(p.key, now);
    return r.from === from && r.to === to;
  });
  return hit ? hit.key : 'custom';
}

/** The same number of days immediately before `from`. */
export function previousRange(from, to) {
  const days = rangeDays(from, to);
  return { from: addDays(from, -days), to: addDays(from, -1) };
}

export const GROUP_OPTIONS = [
  { value: 'day', label: 'Ngày' },
  { value: 'month', label: 'Tháng' },
  { value: 'year', label: 'Năm' },
];

/** day when the range is <= 62 days, month when <= 800 days, otherwise year. */
export function defaultGroupBy(from, to) {
  const days = rangeDays(from, to);
  if (days <= 62) return 'day';
  if (days <= 800) return 'month';
  return 'year';
}

/** day is only allowed for ranges <= 366 days (the server rejects longer ones); month and year always are. */
export function isGroupByAllowed(groupBy, from, to) {
  if (groupBy === 'month' || groupBy === 'year') return true;
  if (groupBy !== 'day') return false;
  const days = rangeDays(from, to);
  return days >= 1 && days <= MAX_DAY_GROUP_DAYS;
}

/** Keeps the user's choice while it is allowed; otherwise (or when never chosen) falls back to the default for the range. */
export function resolveGroupBy(current, from, to, touched = false) {
  return touched && isGroupByAllowed(current, from, to) ? current : defaultGroupBy(from, to);
}

/** Vietnamese message for an invalid range, '' when valid. */
export function validateRange(from, to) {
  if (!isValidIsoDate(from) || !isValidIsoDate(to)) return 'Chọn ngày bắt đầu và ngày kết thúc hợp lệ';
  if (from > to) return 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc';
  if (rangeDays(from, to) > MAX_RANGE_DAYS) return `Khoảng thời gian tối đa ${MAX_RANGE_DAYS} ngày`;
  return '';
}

/** "05/09" from "2026-09-05" ('' when invalid). */
export const formatShortDate = (iso) => (isValidIsoDate(iso) ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}` : '');
/** "05/09/2026" from "2026-09-05" ('' when invalid). */
export const formatDateVi = (iso) => (isValidIsoDate(iso) ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}` : '');

/** "so với kỳ trước (02/08–31/08)" for the toolbar note. */
export function comparisonNote(from, to) {
  if (validateRange(from, to)) return 'so với kỳ trước';
  const prev = previousRange(from, to);
  return `so với kỳ trước (${formatShortDate(prev.from)}–${formatShortDate(prev.to)})`;
}

// ---------------------------------------------------------------- KPI cards

/**
 * format: 'vnd' | 'count' | 'percent'. goodWhen: whether an increase is good ('up') or bad ('down').
 * spark: key of the `series` points that feeds the sparkline (null = none; 'averageOrderValue' is derived).
 * wide: the card spans the full row on phones.
 */
export const KPI_CARDS = [
  { key: 'revenue', label: 'Doanh thu', format: 'vnd', goodWhen: 'up', spark: 'revenue', wide: true },
  { key: 'profit', label: 'Lợi nhuận gộp', format: 'vnd', goodWhen: 'up', spark: 'profit', wide: true },
  { key: 'paidOrders', label: 'Đơn đã thanh toán', format: 'count', goodWhen: 'up', spark: 'paidOrders' },
  { key: 'orders', label: 'Đơn hàng mới', format: 'count', goodWhen: 'up', spark: 'orders' },
  { key: 'averageOrderValue', label: 'Giá trị đơn trung bình', format: 'vnd', goodWhen: 'up', spark: 'averageOrderValue' },
  { key: 'itemsSold', label: 'Sản phẩm đã bán', format: 'count', goodWhen: 'up', spark: null },
  { key: 'newCustomers', label: 'Khách hàng mới', format: 'count', goodWhen: 'up', spark: null },
  { key: 'cancelRate', label: 'Tỷ lệ huỷ đơn', format: 'percent', goodWhen: 'down', spark: null },
];

const FORMATTERS = { vnd: formatVndFull, count: formatCount, percent: (v) => formatPercent(v, 1) };

export function formatKpiValue(cfg, value) {
  if (value === null || value === undefined) return '—';
  return (FORMATTERS[cfg.format] ?? String)(value);
}

/** 'good' | 'bad' | 'neutral' for a change (scales.delta result or a bare direction); a rising cancel rate is bad. */
export function deltaTone(cfg, change) {
  const direction = typeof change === 'string' ? change : change?.direction;
  if (direction !== 'up' && direction !== 'down') return 'neutral';
  const up = direction === 'up';
  return up === (cfg.goodWhen !== 'down') ? 'good' : 'bad';
}

/**
 * What the delta badge shows for a KPI: { kind: 'none'|'new'|'change'|'na', direction, tone, text, label }.
 *  - both 0 -> "—" (kind none); previous 0 and current > 0 -> "Mới"; a missing value (unknown profit) -> na (no badge);
 *  - otherwise the relative change ("+12,5%"); for percent KPIs the change in percentage points ("+2,1 điểm").
 * `label` is the sentence for assistive tech.
 */
export function deltaView(cfg, current, previous) {
  const d = delta(current, previous);
  if (!d) return { kind: 'na', direction: 'flat', tone: 'neutral', text: '', label: 'Chưa có dữ liệu so sánh' };
  if (current === 0 && previous === 0) return { kind: 'none', direction: 'flat', tone: 'neutral', text: '—', label: 'Không đổi so với kỳ trước' };
  if (previous === 0) {
    return { kind: 'new', direction: d.direction, tone: deltaTone(cfg, d), text: 'Mới', label: 'Mới phát sinh so với kỳ trước' };
  }
  if (cfg.format === 'percent') {
    const points = Math.round(d.abs * 1000) / 10;
    const body = Math.abs(points).toLocaleString('vi-VN', { maximumFractionDigits: 1 });
    if (points === 0) return { kind: 'change', direction: 'flat', tone: 'neutral', text: '0 điểm', label: 'Không đổi so với kỳ trước' };
    return {
      kind: 'change',
      direction: d.direction,
      tone: deltaTone(cfg, d),
      text: `${points > 0 ? '+' : '−'}${body} điểm`,
      label: `${points > 0 ? 'Tăng' : 'Giảm'} ${body} điểm phần trăm so với kỳ trước`,
    };
  }
  const text = formatSignedPercent(d.pct);
  if (text === '0%') return { kind: 'change', direction: 'flat', tone: 'neutral', text, label: 'Không đổi so với kỳ trước' };
  return {
    kind: 'change',
    direction: d.direction,
    tone: deltaTone(cfg, d),
    text,
    label: `${d.direction === 'up' ? 'Tăng' : 'Giảm'} ${formatPercent(Math.abs(d.pct))} so với kỳ trước`,
  };
}

/** Coverage sentence of the profit KPI ("Dựa trên 62% giá trị hàng đã bán có giá vốn"); a hint when nothing has a cost. */
export function profitNote(profit) {
  if (!profit || profit.value === null || profit.value === undefined) return 'Nhập giá vốn ở trang Sản phẩm để xem lợi nhuận';
  const c = Number(profit.coverage);
  const ratio = Number.isFinite(c) ? Math.max(0, Math.min(1, c)) : 0;
  // Never round a partial coverage up to a full 100%.
  const pct = ratio >= 1 ? 100 : Math.min(99, Math.floor(ratio * 100));
  return `Dựa trên ${pct}% giá trị hàng đã bán có giá vốn`;
}

const num = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : null);

/** Values of one `series` key for the KPI sparkline (null for unknown/gap); averageOrderValue = revenue / paidOrders. */
export function sparkValues(series, key) {
  if (!key || !Array.isArray(series)) return [];
  if (key === 'averageOrderValue') {
    return series.map((p) => (num(p?.paidOrders) > 0 && num(p?.revenue) !== null ? p.revenue / p.paidOrders : null));
  }
  return series.map((p) => num(p?.[key]));
}

// ---------------------------------------------------------------- Chart / table adapters

export const bucketFormatters = (groupBy) => ({
  formatX: (b) => formatBucket(b, groupBy),
  formatXLong: (b) => formatBucketLong(b, groupBy),
});

/** Series points as the charts expect them (numbers, null for an unknown profit). */
export const chartPoints = (series) => (Array.isArray(series) ? series : []).map((p) => ({
  bucket: p.bucket,
  revenue: num(p.revenue) ?? 0,
  orders: num(p.orders) ?? 0,
  paidOrders: num(p.paidOrders) ?? 0,
  profit: num(p.profit),
}));

export function revenueTable(series, groupBy) {
  return {
    columns: [
      { key: 'bucket', label: 'Kỳ', format: (b) => formatBucketLong(b, groupBy) },
      { key: 'revenue', label: 'Doanh thu', align: 'right', format: formatVndFull },
      { key: 'paidOrders', label: 'Đơn đã thanh toán', align: 'right', format: formatCount },
    ],
    rows: chartPoints(series).map((p) => ({ ...p, key: p.bucket })),
  };
}

export function ordersTable(series, groupBy) {
  return {
    columns: [
      { key: 'bucket', label: 'Kỳ', format: (b) => formatBucketLong(b, groupBy) },
      { key: 'orders', label: 'Đơn hàng', align: 'right', format: formatCount },
    ],
    rows: chartPoints(series).map((p) => ({ ...p, key: p.bucket })),
  };
}

/** Status breakdown in workflow order (every known status, zero-filled), with the share of orders as secondary text. */
export function statusItems(breakdown) {
  const counts = new Map((breakdown || []).map((s) => [s.status, num(s.count) ?? 0]));
  const total = [...counts.values()].reduce((a, b) => a + b, 0);
  const known = Object.keys(ORDER_STATUS_LABEL);
  const order = [...known, ...[...counts.keys()].filter((k) => !known.includes(k))];
  return order
    .filter((status) => counts.has(status))
    .map((status) => {
      const count = counts.get(status);
      const share = total > 0 ? count / total : 0;
      return { key: status, status, label: ORDER_STATUS_LABEL[status] ?? status, value: count, share, secondary: total > 0 ? formatPercent(share) : undefined };
    });
}

export const statusTable = (breakdown) => ({
  columns: [
    { key: 'label', label: 'Trạng thái' },
    { key: 'value', label: 'Số đơn', align: 'right', format: formatCount },
    { key: 'share', label: 'Tỷ lệ', align: 'right', format: (v) => formatPercent(v) },
  ],
  rows: statusItems(breakdown),
});

export const PAYMENT_NAME = { MOMO: 'MoMo', COD: 'COD' };
const paymentName = (m) => PAYMENT_NAME[m] ?? String(m);

/** Parts for the part-to-whole bar: revenue per payment method (both methods always listed). */
export const paymentParts = (breakdown) => (breakdown || []).map((p) => ({ key: p.method, label: paymentName(p.method), value: num(p.revenue) ?? 0 }));

export const paymentTable = (breakdown) => ({
  columns: [
    { key: 'label', label: 'Phương thức' },
    { key: 'orders', label: 'Số đơn', align: 'right', format: formatCount },
    { key: 'revenue', label: 'Doanh thu', align: 'right', format: formatVndFull },
  ],
  rows: (breakdown || []).map((p) => ({ key: p.method, label: paymentName(p.method), orders: num(p.orders) ?? 0, revenue: num(p.revenue) ?? 0 })),
});

/** Top products by revenue for HBarList: the quantity sold is the secondary text. */
export const topProductItems = (products) => (products || []).map((p, i) => ({
  key: `${i}-${p.productName}`,
  label: p.productName,
  value: num(p.revenue) ?? 0,
  secondary: `Đã bán ${formatCount(num(p.quantity) ?? 0)}`,
}));

export const topProductsTable = (products) => ({
  columns: [
    { key: 'productName', label: 'Sản phẩm' },
    { key: 'quantity', label: 'Số lượng', align: 'right', format: formatCount },
    { key: 'revenue', label: 'Doanh thu', align: 'right', format: formatVndFull },
    { key: 'profit', label: 'Lợi nhuận', align: 'right', format: (v) => (v === null || v === undefined ? '—' : formatVndFull(v)) },
  ],
  rows: (products || []).map((p, i) => ({ ...p, key: `${i}-${p.productName}` })),
});

/** True when nothing happened in the period: no orders created and no paid orders. */
export function isEmptyPeriod(data) {
  const k = data?.kpis;
  if (!k) return false;
  return (k.orders?.value ?? 0) === 0 && (k.paidOrders?.value ?? 0) === 0;
}

/** Stock level at or below which a variant is flagged as running out. */
export const LOW_STOCK_WARN = 2;
