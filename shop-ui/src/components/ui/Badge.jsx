import './Badge.css';

/** Small status label. tone: neutral | success | warn | danger | info | accent. `dot` adds a leading dot. */
export default function Badge({ tone = 'neutral', dot = false, className = '', children, ...rest }) {
  return (
    <span className={`ui-badge ui-badge--${tone} ${className}`} {...rest}>
      {dot && <span className="ui-badge__dot" aria-hidden="true" />}
      {children}
    </span>
  );
}
