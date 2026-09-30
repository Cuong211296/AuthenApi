import './Chip.css';

/** Toggle chip: a button with `aria-pressed`. Pass `selected` and `onClick`. */
export default function Chip({ selected = false, className = '', children, ...rest }) {
  return (
    <button type="button" aria-pressed={selected} className={`ui-chip ${selected ? 'is-selected' : ''} ${className}`} {...rest}>
      {children}
    </button>
  );
}
