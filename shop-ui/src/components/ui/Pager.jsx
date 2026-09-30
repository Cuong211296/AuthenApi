import { pageItems } from '../../utils/pagination.js';
import { ChevronIcon } from './icons.jsx';
import './Pager.css';

/** Pagination for zero-based `page` out of `totalPages`; calls `onChange(nextPage)`. Hidden when one page. */
export default function Pager({ page, totalPages, onChange, label = 'Phân trang' }) {
  if (totalPages <= 1) return null;
  return (
    <nav className="ui-pager" aria-label={label}>
      <button type="button" className="ui-pager__step" disabled={page <= 0} onClick={() => onChange(page - 1)} aria-label="Trang trước">
        <ChevronIcon direction="left" size={18} />
        <span className="ui-pager__step-text">Trước</span>
      </button>
      <ol className="ui-pager__pages">
        {pageItems(page, totalPages).map((p, i) => (
          <li key={p === 'gap' ? `gap-${i}` : p}>
            {p === 'gap' ? (
              <span className="ui-pager__gap" aria-hidden="true">…</span>
            ) : (
              <button
                type="button"
                className={`ui-pager__page tabular ${p === page ? 'is-current' : ''}`}
                aria-current={p === page ? 'page' : undefined}
                aria-label={`Trang ${p + 1}`}
                onClick={() => p !== page && onChange(p)}
              >
                {p + 1}
              </button>
            )}
          </li>
        ))}
      </ol>
      <p className="ui-pager__status" aria-hidden="true">Trang {page + 1} / {totalPages}</p>
      <button type="button" className="ui-pager__step" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} aria-label="Trang sau">
        <span className="ui-pager__step-text">Sau</span>
        <ChevronIcon direction="right" size={18} />
      </button>
    </nav>
  );
}
