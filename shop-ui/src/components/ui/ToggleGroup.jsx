import { useId } from 'react';
import { LayoutGroup, motion, useReducedMotion } from 'framer-motion';
import { SPRING } from '../../motion/variants.js';
import './Chip.css';

/**
 * Single-select group of chips (e.g. sizes). options: [{ value, label?, disabled? }].
 * Each chip is a toggle button with `aria-pressed`; the selected highlight slides between chips.
 */
export default function ToggleGroup({ options, value, onChange, label, className = '' }) {
  const groupId = useId();
  const reduce = useReducedMotion();
  return (
    <LayoutGroup id={groupId}>
      <div className={`ui-toggle-group ${className}`} role="group" aria-label={label}>
        {options.map((o) => {
          const selected = o.value === value;
          return (
            <button
              key={o.value}
              type="button"
              aria-pressed={selected}
              disabled={o.disabled}
              className={`ui-chip ui-chip--in-group ${selected ? 'is-selected' : ''}`}
              onClick={() => onChange(o.value)}
            >
              {selected && (
                <motion.span
                  layoutId="ui-toggle-highlight"
                  className="ui-chip__highlight"
                  transition={reduce ? { duration: 0 } : SPRING}
                  aria-hidden="true"
                />
              )}
              <span className="ui-chip__label">{o.label ?? o.value}</span>
            </button>
          );
        })}
      </div>
    </LayoutGroup>
  );
}
