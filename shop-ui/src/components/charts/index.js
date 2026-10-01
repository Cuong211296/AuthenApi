/**
 * SVG chart kit for the admin statistics (no chart library). Follows the dataviz skill: one y axis per chart, thin
 * marks, hairline recessive chrome, selective direct labels, legend only for >= 2 series, text in text tokens, a
 * table view for every chart, keyboard + pointer readouts, reduced-motion aware (framer-motion `useReducedMotion`).
 * Colors come from ./palette.js (validated; see its header). Formatting helpers live in ./scales.js.
 *
 * ChartFrame  - card wrapper.
 *   title, subtitle?, value? (headline node), legend? [{ key, label, color, mark: 'line'|'rect' }] (shown when >= 2),
 *   table? { columns: [{ key, label, align?: 'right', format?(value, row) }], rows: [{ key?, ... }] } -> enables the
 *   "Xem dạng bảng" toggle, loading?, stale? (dim while refetching), empty?, emptyTitle?, emptyText?, footer?,
 *   label? (aria-label, defaults to title), height? (skeleton height, default 260), children (the chart).
 *
 * AreaChart   - time series on ONE axis; area wash + end marker + direct label of the last value for a single series.
 *   data [{ [xKey]: bucket, [series.key]: number|null }], xKey = 'bucket', series [{ key, label, color?, format? }],
 *   height = 260 (min 220, includes the x-axis band), label (aria), area = true, curve = 'monotone'|'linear',
 *   formatX = formatBucket, formatXLong = formatBucketLong (tooltip/announce), formatY = formatVndCompact (axis and
 *   direct label), formatValue = formatVndFull (tooltip), integer = false (integer ticks for counts).
 *
 * BarChart    - vertical bars for ONE series over time (orders). Same props as AreaChart (series has one item) plus
 *   labelMax = true (direct label on the tallest bar). Defaults: formatY/formatValue = formatCount, integer = true.
 *
 *   Both: pointer/touch shows a tooltip at the nearest x (crosshair on AreaChart; column hit target on BarChart);
 *   the plot is focusable: Left/Right move, Home/End jump, Escape clears; the active point is read out in an
 *   aria-live="polite" region.
 *
 * HBarList    - ranked horizontal bars. items [{ key?, label, value, secondary? }] (pre-sorted), formatValue =
 *   formatVndFull, color = slot 1, label (aria-label of the list), ranked = true. Long labels truncate (full text in
 *   the title); negative/zero values draw no bar.
 *
 * StackedBar  - part-to-whole for <= 5 parts (payment methods). parts [{ key, label, value, color? }], formatValue,
 *   totalLabel = 'Tổng', label (aria), emptyText. Always shows the legend list with value and share.
 *
 * Sparkline   - tiny aria-hidden trend line. values (number|null)[], width = 120, height = 32, color (gray),
 *   endColor (accent). The parent states the trend in text.
 *
 * ChartTooltip, ChartLegend, ChartTable - building blocks used by the components above.
 */
export { default as ChartFrame, ChartLegend, ChartTable } from './ChartFrame.jsx';
export { default as AreaChart } from './AreaChart.jsx';
export { default as BarChart } from './BarChart.jsx';
export { default as HBarList } from './HBarList.jsx';
export { default as StackedBar } from './StackedBar.jsx';
export { default as Sparkline } from './Sparkline.jsx';
export { default as ChartTooltip } from './ChartTooltip.jsx';
export * from './scales.js';
export * from './palette.js';
