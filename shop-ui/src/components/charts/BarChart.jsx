import { useMemo } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import CartesianPlot from './CartesianPlot.jsx';
import { AXIS_FONT, barPath, clampLabelX, maxIndex } from './layout.js';
import { estimateTextWidth, formatCount } from './scales.js';
import { EASE } from '../../motion/variants.js';

/**
 * Vertical bars for ONE time series (e.g. orders per day). Bars are <= 24px, 2px apart, square at the baseline with a
 * 4px rounded data end; up to ~92 bars narrow down and the x labels thin out. The tallest bar carries the only
 * direct label. The whole column is the hover/touch target; the hovered bar stays solid while the others dim.
 * Defaults to count formatting with integer ticks (override formatY/formatValue/integer for money).
 */
export default function BarChart({ integer = true, formatY = formatCount, formatValue = formatCount, labelMax = true, ...props }) {
  const reduce = useReducedMotion();
  const { data = [], series = [] } = props;
  const drawKey = useMemo(() => data.map((d) => d?.[series[0]?.key]).join(','), [data, series]);
  const n = data.length;
  const stagger = n > 0 ? Math.min(0.025, 0.45 / n) : 0;

  function renderMarks(layout, active) {
    const s = layout.series[0];
    if (!s) return null;
    const values = layout.columns[0];
    const { xs, y, baselineY, bandwidth, width, top } = layout;
    const peak = labelMax ? maxIndex(values) : -1;
    const peakText = peak >= 0 ? layout.formatY(values[peak]) : '';
    const peakW = estimateTextWidth(peakText, AXIS_FONT);
    return (
      <g key={drawKey} className={`ch-bars ${active !== null ? 'has-active' : ''}`}>
        {values.map((v, i) => {
          if (v === null || v === 0) return null;
          const d = barPath(xs[i], bandwidth, baselineY, y(v));
          if (!d) return null;
          return (
            <motion.path
              key={i}
              className={`ch-bar ${active === i ? 'is-active' : ''}`}
              d={d}
              fill={s.color}
              style={{ originY: v >= 0 ? 1 : 0, transformBox: 'fill-box' }}
              initial={reduce ? false : { scaleY: 0 }}
              animate={{ scaleY: 1 }}
              transition={{ duration: 0.5, delay: i * stagger, ease: EASE }}
            />
          );
        })}
        {peak >= 0 && active === null && (
          <motion.text
            className="ch-direct-label"
            x={clampLabelX(xs[peak], peakW, width)}
            y={Math.max(top - 6, y(values[peak]) - 8)}
            textAnchor="middle"
            initial={reduce ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3, delay: reduce ? 0 : 0.5 }}
          >
            {peakText}
          </motion.text>
        )}
      </g>
    );
  }

  return (
    <CartesianPlot
      {...props}
      band
      endLabel={labelMax}
      integer={integer}
      formatY={formatY}
      formatValue={formatValue}
      renderMarks={renderMarks}
    />
  );
}
