import { useId, useRef } from 'react';
import { LayoutGroup, motion, useReducedMotion } from 'framer-motion';
import { SPRING } from '../../motion/variants.js';
import { CheckIcon } from './icons.jsx';
import './RadioCards.css';

/**
 * Single-select cards with radio semantics (role="radiogroup" + role="radio", roving tabindex).
 * Arrow keys move the selection (and focus), wrapping at the ends. options: [{ value, title, description, icon }].
 */
export default function RadioCards({ label, value, onChange, options, disabled = false, className = '' }) {
  const groupId = useId();
  const reduce = useReducedMotion();
  const refs = useRef([]);
  const selected = options.findIndex((o) => o.value === value);

  function onKeyDown(e, index) {
    const last = options.length - 1;
    const next = { ArrowRight: index + 1, ArrowDown: index + 1, ArrowLeft: index - 1, ArrowUp: index - 1, Home: 0, End: last }[e.key];
    if (next === undefined) return;
    e.preventDefault();
    const target = next < 0 ? last : next > last ? 0 : next;
    onChange(options[target].value);
    refs.current[target]?.focus();
  }

  return (
    <LayoutGroup id={groupId}>
      <div className={`ui-radios ${className}`} role="radiogroup" aria-label={label}>
        {options.map((option, index) => {
          const checked = index === selected;
          return (
            <button
              key={option.value}
              ref={(el) => { refs.current[index] = el; }}
              type="button"
              role="radio"
              aria-checked={checked}
              disabled={disabled}
              tabIndex={checked || (selected < 0 && index === 0) ? 0 : -1}
              className={`ui-radios__card ${checked ? 'is-checked' : ''}`}
              onClick={() => onChange(option.value)}
              onKeyDown={(e) => onKeyDown(e, index)}
            >
              {checked && (
                <motion.span
                  layoutId="ui-radios-ring"
                  className="ui-radios__ring"
                  transition={reduce ? { duration: 0 } : SPRING}
                  aria-hidden="true"
                />
              )}
              <span className="ui-radios__icon" aria-hidden="true">{option.icon}</span>
              <span className="ui-radios__text">
                <span className="ui-radios__title">{option.title}</span>
                {option.description && <span className="ui-radios__desc">{option.description}</span>}
              </span>
              <span className="ui-radios__mark" aria-hidden="true"><CheckIcon size={14} strokeWidth={2.6} /></span>
            </button>
          );
        })}
      </div>
    </LayoutGroup>
  );
}
