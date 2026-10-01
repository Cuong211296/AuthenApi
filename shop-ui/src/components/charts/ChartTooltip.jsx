import { useLayoutEffect, useRef, useState } from 'react';
import { tooltipLeft } from './layout.js';

/**
 * Floating readout for the active point. `x` is the anchor in px inside the positioned plot; the tooltip sits to its
 * right, flips to the left when it would overflow and is clamped inside [0, containerWidth] using its measured
 * width (so it never leaves the plot, even on 390 px screens). rows: [{ key, color, label, value, mark: 'line'|'rect' }].
 * Values lead (strong), series names follow (muted); keys are short strokes of the series color. Hidden from
 * assistive tech: the plot announces the same text through its live region.
 */
export default function ChartTooltip({ x, y = 0, containerWidth, title, rows }) {
  const ref = useRef(null);
  const [width, setWidth] = useState(0);
  // Measured before paint, so the first frame is already placed correctly.
  useLayoutEffect(() => {
    const w = ref.current?.offsetWidth ?? 0;
    setWidth((prev) => (prev === w ? prev : w));
  });
  return (
    <div
      ref={ref}
      className="ch-tip"
      aria-hidden="true"
      style={{ left: tooltipLeft(x, width, containerWidth), top: y }}
    >
      {title && <div className="ch-tip__title">{title}</div>}
      <ul className="ch-tip__rows">
        {rows.map((row) => (
          <li key={row.key} className="ch-tip__row">
            <span className={`ch-key ch-key--${row.mark ?? 'line'}`} style={{ '--ch-key': row.color }} />
            <span className="ch-tip__value">{row.value}</span>
            {row.label && <span className="ch-tip__label">{row.label}</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}
