import './Button.css';

/**
 * Button. variant: primary | dark | ghost | danger; size: sm | md | lg.
 * `loading` disables it and shows a spinner (keeps the label for width/screen readers).
 * `as` renders another element/component (e.g. react-router Link) with the same styling.
 */
export default function Button({
  as: Tag = 'button',
  variant = 'primary',
  size = 'md',
  loading = false,
  block = false,
  iconLeft,
  iconRight,
  className = '',
  disabled,
  children,
  ...rest
}) {
  const classes = ['ui-btn', `ui-btn--${variant}`, `ui-btn--${size}`, block && 'ui-btn--block', loading && 'is-loading', className]
    .filter(Boolean)
    .join(' ');
  const isButton = Tag === 'button';
  return (
    <Tag
      className={classes}
      {...(isButton ? { type: rest.type ?? 'button', disabled: disabled || loading } : { 'aria-disabled': disabled || undefined })}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <span className="spinner ui-btn__spinner" aria-hidden="true" />}
      {!loading && iconLeft}
      <span className="ui-btn__label">{children}</span>
      {iconRight}
    </Tag>
  );
}
