import { useId, useRef } from 'react';
import { LayoutGroup, motion, useReducedMotion } from 'framer-motion';
import { SPRING } from '../../motion/variants.js';
import './Tabs.css';

/**
 * Segmented control with a sliding indicator (WAI-ARIA tabs with manual activation).
 * items: [{ value, label }]. Arrow keys / Home / End move focus; Enter or Space selects.
 * `panelId` (optional) is the id of the results container the tabs control; that container should have `role="tabpanel"`
 * and `aria-labelledby={tabDomId(idPrefix, value)}`. `idPrefix` gives each tab the id `${idPrefix}-tab-${value}`.
 * When `value` matches no item (deep link not loaded yet or unknown) nothing is shown as selected, and the first tab
 * stays reachable with the keyboard.
 */
export const tabDomId = (idPrefix, value) => `${idPrefix}-tab-${value}`;

export default function Tabs({ items, value, onChange, label, panelId, idPrefix, className = '' }) {
  const groupId = useId();
  const reduce = useReducedMotion();
  const refs = useRef([]);
  const selectedIndex = items.findIndex((i) => i.value === value);

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
              id={idPrefix ? tabDomId(idPrefix, item.value) : undefined}
              aria-controls={panelId}
              tabIndex={selected || (selectedIndex < 0 && index === 0) ? 0 : -1}
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
