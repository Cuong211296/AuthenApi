import { colorSwatch } from '../../utils/colors.js';
import './Swatch.css';

/**
 * Color option. Renders a dot when the color name is known (see utils/colors.js), otherwise a text chip.
 * Toggle button semantics (`aria-pressed`); unavailable colors are disabled and crossed out.
 */
export default function Swatch({ color, selected = false, available = true, onClick, label }) {
  const hex = colorSwatch(color);
  const name = label ?? color;
  const text = available ? name : `${name} (hết hàng)`;
  if (!hex) {
    return (
      <button type="button" aria-pressed={selected} disabled={!available} onClick={onClick}
        className={`ui-swatch-text ${selected ? 'is-selected' : ''}`} title={text}>
        {name}
      </button>
    );
  }
  return (
    <button type="button" aria-pressed={selected} disabled={!available} onClick={onClick}
      className={`ui-swatch ${selected ? 'is-selected' : ''}`} aria-label={text} title={text}>
      <span className="ui-swatch__dot" style={{ background: hex }} />
      {!available && <span className="ui-swatch__cross" aria-hidden="true" />}
    </button>
  );
}
