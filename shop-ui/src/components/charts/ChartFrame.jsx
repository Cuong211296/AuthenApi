import { useState } from 'react';
import EmptyState from '../ui/EmptyState.jsx';
import { Skeleton } from '../ui/Skeleton.jsx';
import { ChartIcon, TableIcon } from '../ui/icons.jsx';
import './charts.css';

/** Legend for two or more series (a single series is named by the title, so no box). Text uses text tokens. */
export function ChartLegend({ items = [] }) {
  if (items.length < 2) return null;
  return (
    <ul className="ch-legend">
      {items.map((it) => (
        <li key={it.key ?? it.label} className="ch-legend__item">
          <span className={`ch-key ch-key--${it.mark ?? 'line'}`} style={{ '--ch-key': it.color }} aria-hidden="true" />
          {it.label}
        </li>
      ))}
    </ul>
  );
}

/** Accessible data table twin of a chart. columns: [{ key, label, align?: 'right', format?(value, row) }]. */
export function ChartTable({ caption, columns = [], rows = [] }) {
  return (
    <div className="ch-table-wrap" tabIndex={0} role="region" aria-label={`${caption} (bảng)`}>
      <table className="ch-table">
        <caption>{caption}</caption>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} scope="col" className={c.align === 'right' ? 'is-right' : undefined}>{c.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, r) => (
            <tr key={row.key ?? r}>
              {columns.map((c, i) => {
                const content = c.format ? c.format(row[c.key], row) : row[c.key];
                const cls = c.align === 'right' ? 'is-right' : undefined;
                return i === 0
                  ? <th key={c.key} scope="row" className={cls}>{content}</th>
                  : <td key={c.key} className={cls}>{content}</td>;
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Card around one chart: title (Inter, not the display face), optional subtitle and headline value, legend (only
 * for >= 2 items), a "Xem dạng bảng" toggle that swaps the chart for its table twin, loading skeleton, empty state,
 * and `stale` (refetch: keep the previous render dimmed instead of flashing a skeleton).
 */
export default function ChartFrame({
  title,
  subtitle,
  value,
  legend,
  table,
  loading = false,
  stale = false,
  empty = false,
  emptyTitle = 'Chưa có dữ liệu',
  emptyText,
  footer,
  label,
  height = 260,
  className = '',
  children,
}) {
  const [showTable, setShowTable] = useState(false);
  const canTable = Boolean(table) && !loading && !empty;
  let body;
  if (loading) {
    body = (
      <div aria-hidden="true">
        <Skeleton height={height} radius={10} />
      </div>
    );
  } else if (empty) {
    body = (
      <EmptyState className="ch-frame__empty" icon={<ChartIcon size={22} />} title={emptyTitle}>
        {emptyText}
      </EmptyState>
    );
  } else if (showTable && canTable) {
    body = <ChartTable caption={label ?? title} columns={table.columns} rows={table.rows} />;
  } else {
    body = children;
  }

  return (
    <figure
      className={`ch-frame ${className}`}
      role="figure"
      aria-label={label ?? title}
      aria-busy={loading || stale || undefined}
    >
      <div className="ch-frame__head">
        <div className="ch-frame__heading">
          <h3 className="ch-frame__title">{title}</h3>
          {subtitle && <p className="ch-frame__sub">{subtitle}</p>}
          {value != null && !loading && <div className="ch-frame__value">{value}</div>}
        </div>
        {canTable && (
          <button type="button" className="ch-frame__toggle" aria-pressed={showTable} onClick={() => setShowTable((v) => !v)}>
            {showTable ? <ChartIcon size={15} /> : <TableIcon size={15} />}
            {showTable ? 'Xem biểu đồ' : 'Xem dạng bảng'}
          </button>
        )}
      </div>
      {!loading && !empty && !showTable && <ChartLegend items={legend} />}
      <div className="ch-frame__body" style={stale ? { opacity: 0.55, transition: 'opacity 0.2s' } : undefined}>{body}</div>
      {footer && <figcaption className="ch-frame__foot">{footer}</figcaption>}
    </figure>
  );
}
