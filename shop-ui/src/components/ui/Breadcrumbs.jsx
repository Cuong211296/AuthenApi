import { Link } from 'react-router-dom';
import { ChevronIcon } from './icons.jsx';
import './Breadcrumbs.css';

/** Breadcrumb trail. items: [{ label, to? }]; the last item is the current page. */
export default function Breadcrumbs({ items, className = '' }) {
  return (
    <nav aria-label="Breadcrumb" className={`ui-crumbs ${className}`}>
      <ol>
        {items.map((item, i) => {
          const last = i === items.length - 1;
          return (
            <li key={`${item.label}-${i}`}>
              {last || !item.to
                ? <span aria-current={last ? 'page' : undefined}>{item.label}</span>
                : <Link to={item.to}>{item.label}</Link>}
              {!last && <ChevronIcon direction="right" size={14} className="ui-crumbs__sep" />}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
