import { useId } from 'react';
import './Switch.css';

/** On/off switch (role="switch"). `label` is the visible text; `hideLabel` keeps it for screen readers only. */
export default function Switch({ checked, onChange, label, hideLabel = false, disabled = false, className = '', id }) {
  const uid = useId();
  const labelId = `${id ?? uid}-label`;
  return (
    <span className={`ui-switch ${className}`}>
      <button
        type="button"
        role="switch"
        id={id}
        aria-checked={checked}
        aria-labelledby={labelId}
        disabled={disabled}
        className={`ui-switch__track ${checked ? 'is-on' : ''}`}
        onClick={() => onChange(!checked)}
      >
        <span className="ui-switch__knob" />
      </button>
      <span id={labelId} className={hideLabel ? 'sr-only' : 'ui-switch__label'}>{label}</span>
    </span>
  );
}
