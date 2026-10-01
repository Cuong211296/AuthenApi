import { useId } from 'react';
import { LayoutGroup, motion, useReducedMotion } from 'framer-motion';
import { SPRING } from '../../../motion/variants.js';

/**
 * Pill segmented control made of toggle buttons (aria-pressed) with a sliding highlight. Used for filters, so it is a
 * button group rather than tabs. options: [{ value, label, disabled? }]. A `value` that matches no option (e.g. a
 * custom date range) leaves every button unpressed.
 */
export default function Segmented({ options, value, onChange, label, className = '' }) {
  const groupId = useId();
  const reduce = useReducedMotion();
  return (
    <LayoutGroup id={groupId}>
      <div className={`ov-seg ${className}`} role="group" aria-label={label}>
        {options.map((o) => {
          const pressed = o.value === value;
          return (
            <button
              key={o.value}
              type="button"
              className={`ov-seg__btn ${pressed ? 'is-pressed' : ''}`}
              aria-pressed={pressed}
              disabled={o.disabled}
              onClick={() => onChange(o.value)}
            >
              {pressed && (
                <motion.span
                  layoutId="ov-seg-highlight"
                  className="ov-seg__hl"
                  transition={reduce ? { duration: 0 } : SPRING}
                  aria-hidden="true"
                />
              )}
              <span className="ov-seg__label">{o.label}</span>
            </button>
          );
        })}
      </div>
    </LayoutGroup>
  );
}
