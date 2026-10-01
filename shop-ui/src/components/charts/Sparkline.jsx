import { useMemo } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import { extent, linearScale, pathFromPoints } from './scales.js';
import { lastFiniteIndex } from './layout.js';
import { CATEGORICAL, OTHER } from './palette.js';
import { EASE } from '../../motion/variants.js';
import './charts.css';

/**
 * Tiny trend line (no axes) for KPI tiles: 2px line in the de-emphasis gray by default with the latest point as an
 * accent dot. aria-hidden: the parent must state the trend in text (e.g. the delta next to it).
 * values: numbers (null leaves a gap). An all-equal series draws a flat line in the middle.
 */
export default function Sparkline({ values = [], width = 120, height = 32, color = OTHER, endColor = CATEGORICAL[0], className = '' }) {
  const reduce = useReducedMotion();
  const pad = 4;
  const { line, last } = useMemo(() => {
    const { min, max } = extent(values);
    const x = linearScale([0, Math.max(1, values.length - 1)], [pad, width - pad]);
    const y = min === max ? () => height / 2 : linearScale([min, max], [height - pad, pad]);
    const points = values.map((v, i) => ({ x: values.length === 1 ? width / 2 : x(i), y: Number.isFinite(v) ? y(v) : null }));
    const li = lastFiniteIndex(values);
    return { line: pathFromPoints(points, { curve: 'monotone' }).line, last: li >= 0 ? points[li] : null };
  }, [values, width, height]);

  return (
    <svg className={`ch-spark ${className}`} width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden="true" focusable="false">
      {line && (
        <motion.path
          key={values.join(',')}
          d={line}
          fill="none"
          stroke={color}
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
          initial={reduce ? false : { pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ duration: 0.8, ease: EASE }}
        />
      )}
      {last && (
        <motion.circle
          cx={last.x}
          cy={last.y}
          r={3}
          fill={endColor}
          stroke="#ffffff"
          strokeWidth={1.5}
          initial={reduce ? false : { opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.3, delay: reduce ? 0 : 0.7 }}
        />
      )}
    </svg>
  );
}
