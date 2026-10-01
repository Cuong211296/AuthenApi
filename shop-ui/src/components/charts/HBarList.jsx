import { motion, useReducedMotion } from 'framer-motion';
import { formatVndFull } from './scales.js';
import { seriesColor } from './palette.js';
import { EASE } from '../../motion/variants.js';
import { barShare } from './layout.js';
import './charts.css';

/**
 * Ranked horizontal bars: rank, label (truncated, full text in the title), optional secondary line, value at the
 * right, and a thin 8px bar in ONE color (nominal items never get a value ramp). Bar width animates in.
 * items: [{ key?, label, value, secondary? }] in display order (sort before passing). The list itself is the text
 * alternative (every value is printed), so no tooltip is needed beyond the native title.
 */
export default function HBarList({ items = [], formatValue = formatVndFull, color = seriesColor(0), label, ranked = true }) {
  const reduce = useReducedMotion();
  const max = items.reduce((m, it) => (Number.isFinite(it.value) && it.value > m ? it.value : m), 0);
  return (
    <ol className={`ch-hbars ${ranked ? '' : 'ch-hbars--plain'}`} aria-label={label}>
      {items.map((it, i) => {
        const value = formatValue(it.value);
        const share = barShare(it.value, max);
        return (
          <li key={it.key ?? `${i}-${it.label}`} className="ch-hbar" title={`${it.label}: ${value}${it.secondary ? ` · ${it.secondary}` : ''}`}>
            {ranked && <span className="ch-hbar__rank" aria-hidden="true">{i + 1}</span>}
            <span className="ch-hbar__text">
              <span className="ch-hbar__label">{it.label}</span>
              {it.secondary && <span className="ch-hbar__secondary">{it.secondary}</span>}
            </span>
            <span className="ch-hbar__value">{value}</span>
            <span className="ch-hbar__track" aria-hidden="true">
              <motion.span
                className="ch-hbar__bar"
                style={{ width: `${share * 100}%`, minWidth: share > 0 ? 2 : 0, background: color }}
                initial={reduce ? false : { scaleX: 0 }}
                animate={{ scaleX: 1 }}
                transition={{ duration: 0.6, delay: Math.min(i * 0.05, 0.4), ease: EASE }}
              />
            </span>
          </li>
        );
      })}
    </ol>
  );
}
