import { forwardRef, useId, useState } from 'react';
import { ChevronIcon, EyeIcon, EyeOffIcon } from './icons.jsx';
import './Field.css';

/**
 * Labelled form control with an inline message. `as`: 'input' (default) | 'select' | 'textarea'.
 * `error` shows a danger message (aria-invalid + aria-describedby; announced politely), otherwise `hint` is shown.
 * type="password" gets a visibility toggle. Extra props go to the control.
 */
const Field = forwardRef(function Field(
  { label, error, hint, as: Tag = 'input', type, optional = false, className = '', children, ...rest },
  ref,
) {
  const uid = useId();
  const id = rest.id ?? uid;
  const msgId = `${id}-msg`;
  const [shown, setShown] = useState(false);
  const isPassword = type === 'password';
  const message = error || hint;

  return (
    <div className={`ui-field ${error ? 'has-error' : ''} ${className}`}>
      <label htmlFor={id} className="ui-field__label">
        {label}
        {optional && <span className="ui-field__opt"> (không bắt buộc)</span>}
      </label>
      <div className={`ui-field__control ui-field__control--${Tag} ${isPassword ? 'has-toggle' : ''}`}>
        <Tag
          ref={ref}
          id={id}
          type={Tag === 'input' ? (isPassword && shown ? 'text' : type) : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? msgId : undefined}
          {...rest}
        >
          {children}
        </Tag>
        {Tag === 'select' && <ChevronIcon size={18} className="ui-field__chevron" />}
        {isPassword && (
          <button
            type="button"
            className="ui-field__toggle"
            aria-label={shown ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            aria-pressed={shown}
            onClick={() => setShown((s) => !s)}
          >
            {shown ? <EyeOffIcon size={20} /> : <EyeIcon size={20} />}
          </button>
        )}
      </div>
      <div aria-live="polite" className="ui-field__msg-wrap">
        {message && <p id={msgId} className={error ? 'ui-field__error' : 'ui-field__hint'}>{message}</p>}
      </div>
    </div>
  );
});

export default Field;
