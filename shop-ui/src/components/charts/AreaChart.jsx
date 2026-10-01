import { useId, useMemo } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import CartesianPlot from './CartesianPlot.jsx';
import { lastFiniteIndex } from './layout.js';
import { pathFromPoints } from './scales.js';
import { EASE } from '../../motion/variants.js';

/**
 * Time series as 2px lines on ONE y axis (never dual axis). A single series gets a soft area wash, an end-point
 * marker and a direct label of its last value; several series get lines only (pass a legend to ChartFrame).
 * Null values leave gaps. Hover/touch: crosshair snapping to the nearest x + tooltip; keyboard via CartesianPlot.
 */
export default function AreaChart({ area = true, curve = 'monotone', ...props }) {
  const reduce = useReducedMotion();
  const gradientBase = `ch-area-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const single = (props.series?.length ?? 0) === 1;
  const { data = [], series = [] } = props;
  // Replays the draw-in only when the values change, not on resize.
  const drawKey = useMemo(() => series.map((s) => data.map((d) => d?.[s.key]).join(',')).join('|'), [data, series]);

  function renderMarks(layout, active) {
    const { xs, y, columns, baselineY } = layout;
    return (
      <g>
        {layout.series.map((s, k) => {
          const points = columns[k].map((v, i) => ({ x: xs[i], y: v === null ? null : y(v) }));
          const { line, area: areaPath } = pathFromPoints(points, { curve, baseline: baselineY });
          const gid = `${gradientBase}-${k}`;
          const last = single ? lastFiniteIndex(columns[k]) : -1;
          const lastPt = last >= 0 ? points[last] : null;
          return (
            <g key={s.key}>
              {single && area && areaPath && (
                <>
                  <defs>
                    <linearGradient id={gid} x1="0" x2="0" y1="0" y2="1">
                      <stop offset="0%" stopColor={s.color} stopOpacity="0.16" />
                      <stop offset="100%" stopColor={s.color} stopOpacity="0" />
                    </linearGradient>
                  </defs>
                  <motion.path
                    key={`a-${drawKey}`}
                    d={areaPath}
                    fill={`url(#${gid})`}
                    initial={reduce ? false : { opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ duration: 0.6, delay: 0.35, ease: EASE }}
                  />
                </>
              )}
              {line && (
                <motion.path
                  key={`l-${drawKey}`}
                  className="ch-line"
                  d={line}
                  stroke={s.color}
                  initial={reduce ? false : { pathLength: 0 }}
                  animate={{ pathLength: 1 }}
                  transition={{ duration: 0.9, ease: EASE }}
                />
              )}
              {lastPt && (
                <motion.g
                  key={`e-${drawKey}`}
                  initial={reduce ? false : { opacity: 0 }}
                  animate={{ opacity: active === last ? 0 : 1 }}
                  transition={{ duration: 0.3, delay: active === null ? 0.7 : 0 }}
                >
                  <circle className="ch-marker" cx={lastPt.x} cy={lastPt.y} r={5} fill={s.color} />
                  {layout.endLabelText && (
                    <text className="ch-direct-label" x={lastPt.x + 10} y={lastPt.y} dy="0.34em" textAnchor="start">
                      {layout.endLabelText}
                    </text>
                  )}
                </motion.g>
              )}
              {active !== null && points[active]?.y != null && (
                <circle className="ch-marker" cx={points[active].x} cy={points[active].y} r={5.5} fill={s.color} />
              )}
            </g>
          );
        })}
      </g>
    );
  }

  return <CartesianPlot {...props} band={false} endLabel={single} renderMarks={renderMarks} />;
}
