/**
 * Floating readout for the active point. `x` is the anchor in px inside the positioned plot; the tooltip sits to its
 * right and flips to the left past 58% of `containerWidth`. rows: [{ key, color, label, value, mark: 'line'|'rect' }].
 * Values lead (strong), series names follow (muted); keys are short strokes of the series color. Hidden from
 * assistive tech: the plot announces the same text through its live region.
 */
export default function ChartTooltip({ x, y = 0, containerWidth, title, rows }) {
  const flip = x > containerWidth * 0.58;
  return (
    <div
      className="ch-tip"
      aria-hidden="true"
      style={{ left: x, top: y, transform: flip ? 'translateX(calc(-100% - 14px))' : 'translateX(14px)' }}
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
