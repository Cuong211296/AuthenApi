import { useEffect, useMemo, useRef, useState } from 'react';
import ChartTooltip from './ChartTooltip.jsx';
import useElementWidth from './useElementWidth.js';
import { AXIS_FONT, cartesianLayout, clampLabelX, lastFiniteIndex } from './layout.js';
import { estimateTextWidth, formatBucket, formatBucketLong, formatVndCompact, formatVndFull, nearestIndexByX } from './scales.js';
import { seriesColor } from './palette.js';
import './charts.css';

const crisp = (v) => Math.round(v) + 0.5;

/**
 * Shared shell of AreaChart and BarChart: measures the container, lays out one y axis (hairline grid + muted tick
 * labels) and thinned x labels, owns the active index (pointer: nearest x; keyboard: arrows/Home/End, Escape
 * clears), the tooltip and the polite live region. Marks are drawn by `renderMarks(layout, active, columns)` where
 * `columns[k]` holds series k's values.
 */
export default function CartesianPlot({
  data = [],
  xKey = 'bucket',
  series = [],
  height = 260,
  band = false,
  crosshair = !band,
  endLabel = false,
  integer = false,
  label,
  formatX = formatBucket,
  formatXLong = formatBucketLong,
  formatY = formatVndCompact,
  formatValue = formatVndFull,
  renderMarks,
}) {
  const wrapRef = useRef(null);
  const svgRef = useRef(null);
  const width = useElementWidth(wrapRef);
  const [active, setActive] = useState(null);
  const [announcement, setAnnouncement] = useState('');
  const h = Math.max(220, height);

  const colored = useMemo(() => series.map((s, i) => ({ ...s, color: s.color ?? seriesColor(i) })), [series]);
  const columns = useMemo(
    () => colored.map((s) => data.map((d) => (Number.isFinite(d?.[s.key]) ? d[s.key] : null))),
    [colored, data],
  );
  const xLabels = useMemo(() => data.map((d) => String(formatX(d?.[xKey]))), [data, xKey, formatX]);
  // Single line: the last value is labelled to the right of its end point.
  const endLabelText = useMemo(() => {
    if (band || !endLabel || columns.length !== 1) return '';
    const i = lastFiniteIndex(columns[0]);
    return i >= 0 ? String(formatY(columns[0][i])) : '';
  }, [band, endLabel, columns, formatY]);
  const layout = useMemo(
    () => cartesianLayout({ width, height: h, values: columns.flat(), xLabels, formatY, integer, band, endLabel: band && endLabel, endLabelText }),
    [width, h, columns, xLabels, formatY, integer, band, endLabel, endLabelText],
  );

  // A new dataset can be shorter than the active index.
  useEffect(() => { setActive((a) => (a !== null && a >= data.length ? null : a)); }, [data.length]);

  const describe = (i) => {
    const values = colored.map((s, k) => `${s.label}: ${(s.format ?? formatValue)(columns[k][i])}`).join(', ');
    return `${formatXLong(data[i]?.[xKey])}. ${values}`;
  };

  function pointerIndex(e) {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect || data.length === 0) return null;
    return nearestIndexByX(layout.xs, e.clientX - rect.left);
  }
  const onPointerMove = (e) => setActive(pointerIndex(e));
  const onPointerLeave = (e) => { if (e.pointerType === 'mouse') setActive(null); };

  function onKeyDown(e) {
    const n = data.length;
    if (n === 0) return;
    let next;
    if (e.key === 'ArrowRight') next = active === null ? 0 : Math.min(n - 1, active + 1);
    else if (e.key === 'ArrowLeft') next = active === null ? n - 1 : Math.max(0, active - 1);
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = n - 1;
    else if (e.key === 'Escape' && active !== null) {
      e.preventDefault();
      setActive(null);
      setAnnouncement('');
      return;
    } else return;
    e.preventDefault();
    setActive(next);
    setAnnouncement(describe(next));
  }

  const { left, plotW, top, plotH, ticks, tickLabels, y, xs, labelIdx } = layout;
  const ready = width > 0;
  const activeRows = active !== null && data[active]
    ? colored.map((s, k) => ({ key: s.key, color: s.color, label: s.label, value: (s.format ?? formatValue)(columns[k][active]), mark: band ? 'rect' : 'line' }))
    : null;

  return (
    <div
      ref={wrapRef}
      className="ch-plot"
      tabIndex={data.length ? 0 : -1}
      role="group"
      aria-roledescription="biểu đồ"
      aria-label={`${label ?? ''}${data.length ? '. Dùng phím mũi tên trái, phải để xem từng điểm; Esc để bỏ chọn.' : ''}`}
      onKeyDown={onKeyDown}
      onBlur={() => setActive(null)}
    >
      <svg ref={svgRef} width={width || '100%'} height={h} aria-hidden="true" focusable="false">
        {ready && (
          <>
            <g>
              {ticks.map((t, i) => (
                <g key={t}>
                  <line className={t === 0 ? 'ch-baseline' : 'ch-grid'} x1={left} x2={left + plotW} y1={crisp(y(t))} y2={crisp(y(t))} />
                  {tickLabels[i] && (
                    <text className="ch-axis-text" x={left - 10} y={y(t)} dy="0.34em" textAnchor="end">{tickLabels[i]}</text>
                  )}
                </g>
              ))}
            </g>
            <g>
              {labelIdx.map((i) => {
                const w = estimateTextWidth(xLabels[i], AXIS_FONT);
                return (
                  <text key={i} className="ch-axis-text" x={clampLabelX(xs[i], w, width)} y={top + plotH + 20} textAnchor="middle">
                    {xLabels[i]}
                  </text>
                );
              })}
            </g>
            {crosshair && active !== null && xs[active] !== undefined && (
              <line className="ch-crosshair" x1={crisp(xs[active])} x2={crisp(xs[active])} y1={top} y2={top + plotH} />
            )}
            {renderMarks({ ...layout, columns, series: colored, xLabels, formatY, endLabelText }, active)}
            <rect
              className={`ch-hit ${band ? 'ch-bars-hit' : ''}`}
              x={left - 8}
              y={top - 8}
              width={Math.max(0, width - left + 8)}
              height={plotH + 8}
              onPointerMove={onPointerMove}
              onPointerDown={onPointerMove}
              onPointerLeave={onPointerLeave}
            />
          </>
        )}
      </svg>
      {ready && activeRows && (
        <ChartTooltip
          x={xs[active]}
          y={top}
          containerWidth={width}
          title={formatXLong(data[active][xKey])}
          rows={activeRows}
        />
      )}
      <div className="ch-sr" aria-live="polite" aria-atomic="true">{announcement}</div>
    </div>
  );
}
