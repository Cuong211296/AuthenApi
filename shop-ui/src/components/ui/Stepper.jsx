import { useEffect, useState } from 'react';
import { MinusIcon, PlusIcon } from './icons.jsx';
import { clampCommit } from '../../utils/quantity.js';
import './Stepper.css';

/**
 * Quantity stepper (- / value / +). Controlled: `value` + `onChange(next)`; `onChange` only ever receives
 * a clamped integer. Buttons disable at the bounds; typed values are committed on blur or Enter.
 */
export default function Stepper({ value, onChange, min = 1, max = 99, disabled = false, label = 'Số lượng', size = 'md', id }) {
  const [draft, setDraft] = useState(String(value));
  useEffect(() => setDraft(String(value)), [value]);

  const commit = (next) => {
    const clamped = clampCommit(next, value, min, max);
    if (clamped === null) {
      setDraft(String(value));
      return;
    }
    setDraft(String(clamped));
    onChange(clamped);
  };

  return (
    <div className={`ui-stepper ui-stepper--${size}`} role="group" aria-label={label}>
      <button type="button" className="ui-stepper__btn" aria-label="Giảm số lượng" disabled={disabled || value <= min} onClick={() => commit(value - 1)}>
        <MinusIcon size={16} />
      </button>
      <input
        id={id}
        className="ui-stepper__input tabular"
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        aria-label={label}
        value={draft}
        disabled={disabled}
        onChange={(e) => setDraft(e.target.value.replace(/\D/g, '').slice(0, 3))}
        onBlur={() => commit(draft)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') { e.preventDefault(); commit(draft); }
          if (e.key === 'ArrowUp') { e.preventDefault(); commit(value + 1); }
          if (e.key === 'ArrowDown') { e.preventDefault(); commit(value - 1); }
        }}
      />
      <button type="button" className="ui-stepper__btn" aria-label="Tăng số lượng" disabled={disabled || value >= max} onClick={() => commit(value + 1)}>
        <PlusIcon size={16} />
      </button>
    </div>
  );
}
