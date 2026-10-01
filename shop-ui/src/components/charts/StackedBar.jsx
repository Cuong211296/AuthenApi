import { useState } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import { formatPercent, formatVndFull } from './scales.js';
import { seriesColor } from './palette.js';
import { EASE } from '../../motion/variants.js';
import { partShares } from './layout.js';
import './charts.css';

/**
 * Part-to-whole for <= 5 parts (e.g. MoMo vs COD) as one horizontal 100% bar - the dataviz skill's form for
 * part-to-whole (a donut is deprioritized). 2px surface gaps between segments, 4px rounded outer ends. The legend list
 * below is always shown and is the text alternative: color key, label, value and share for every part.
 * parts: [{ key, label, value, color? }]. Hovering a segment or a legend row dims the other
 * segments; each segment also has a native title readout.
 */
export default function StackedBar({ parts = [], formatValue = formatVndFull, totalLabel = 'Tổng', label, emptyText = 'Chưa có dữ liệu' }) {
  const reduce = useReducedMotion();
  const [active, setActive] = useState(null);
  const { total, shares } = partShares(parts.map((p) => p.value));
  const colored = parts.map((p, i) => ({ ...p, color: p.color ?? seriesColor(i), share: shares[i] }));

  return (
    <div className="ch-stack">
      <div className="ch-stack__total">
        <span>{totalLabel}</span>
        <strong>{formatValue(total)}</strong>
      </div>
      <div className="ch-stack__bar" role="img" aria-label={`${label ? `${label}: ` : ''}${colored.map((p) => `${p.label} ${formatPercent(p.share)}`).join(', ')}`}>
        {total > 0 ? (
          colored.filter((p) => p.share > 0).map((p, i) => (
            <motion.span
              key={p.key}
              className={`ch-stack__seg ${active !== null && active !== p.key ? 'is-dim' : ''}`}
              style={{ flexGrow: p.share, flexBasis: 0, background: p.color }}
              title={`${p.label}: ${formatValue(p.value)} (${formatPercent(p.share)})`}
              onPointerEnter={() => setActive(p.key)}
              onPointerLeave={() => setActive(null)}
              initial={reduce ? false : { scaleX: 0, opacity: 0 }}
              animate={{ scaleX: 1, opacity: 1 }}
              transition={{ duration: 0.55, delay: i * 0.08, ease: EASE }}
            />
          ))
        ) : (
          <span className="ch-stack__empty" title={emptyText} />
        )}
      </div>
      <ul className="ch-stack__list">
        {colored.map((p) => (
          <li
            key={p.key}
            className="ch-stack__item"
            onPointerEnter={() => setActive(p.key)}
            onPointerLeave={() => setActive(null)}
          >
            <span className="ch-key ch-key--rect" style={{ '--ch-key': p.color }} aria-hidden="true" />
            <span className="ch-stack__label">{p.label}</span>
            <span className="ch-stack__value">{formatValue(p.value)}</span>
            <span className="ch-stack__pct">{total > 0 ? formatPercent(p.share) : '—'}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
