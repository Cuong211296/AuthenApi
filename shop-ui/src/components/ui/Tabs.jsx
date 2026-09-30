import { useId, useRef } from 'react';
import { LayoutGroup, motion, useReducedMotion } from 'framer-motion';
import { SPRING } from '../../motion/variants.js';
import './Tabs.css';

/**
 * Segmented control with a sliding indicator (WAI-ARIA tabs with manual activation).
 * items: [{ value, label }]. Arrow keys / Home / End move focus; Enter or Space selects.
 * `panelId` (optional) is the id of the element the tabs control.
 */
export default function Tabs({ items, value, onChange, label, panelId, className = '' }) {
  const groupId = useId();
  const reduce = useReducedMotion();
  const refs = useRef([]);
  const selectedIndex = Math.max(0, items.findIndex((i) => i.value === value));

  function onKeyDown(e, index) {
    const last = items.length - 1;
    const next = { ArrowRight: index + 1, ArrowLeft: index - 1, Home: 0, End: last }[e.key];
    if (next === undefined) return;
    e.preventDefault();
    const target = next < 0 ? last : next > last ? 0 : next;
    refs.current[target]?.focus();
  }

  return (
    <LayoutGroup id={groupId}>
      <div className={`ui-tabs ${className}`} role="tablist" aria-label={label}>
        {items.map((item, index) => {
          const selected = index === selectedIndex;
          return (
            <button
              key={item.value}
              ref={(el) => { refs.current[index] = el; }}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={panelId}
              tabIndex={selected ? 0 : -1}
              className={`ui-tabs__tab ${selected ? 'is-selected' : ''}`}
              onClick={() => onChange(item.value)}
              onKeyDown={(e) => onKeyDown(e, index)}
            >
              {selected && (
                <motion.span
                  layoutId="ui-tabs-indicator"
                  className="ui-tabs__indicator"
                  transition={reduce ? { duration: 0 } : SPRING}
                  aria-hidden="true"
                />
              )}
              <span className="ui-tabs__label">{item.label}</span>
            </button>
          );
        })}
      </div>
    </LayoutGroup>
  );
}
