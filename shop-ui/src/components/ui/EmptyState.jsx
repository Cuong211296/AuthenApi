import { BagIcon } from './icons.jsx';
import './EmptyState.css';

/** Friendly empty/error message: icon, title, optional text and action (any node, e.g. a Button). */
export default function EmptyState({ icon, title, children, action, tone = 'neutral', className = '', role }) {
  return (
    <div className={`ui-empty ui-empty--${tone} ${className}`} role={role}>
      <div className="ui-empty__art" aria-hidden="true">
        <span className="ui-empty__ring" />
        <span className="ui-empty__icon">{icon ?? <BagIcon size={28} />}</span>
      </div>
      <h2 className="ui-empty__title">{title}</h2>
      {children && <div className="ui-empty__text">{children}</div>}
      {action && <div className="ui-empty__action">{action}</div>}
    </div>
  );
}
