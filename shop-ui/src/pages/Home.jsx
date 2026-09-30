import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';
import ProductCard from '../components/ProductCard.jsx';

export default function Home() {
  const [params, setParams] = useSearchParams();
  const category = params.get('category') || '';
  const q = params.get('q') || '';
  const page = Number(params.get('page') || 0);

  const [categories, setCategories] = useState([]);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [search, setSearch] = useState(q);

  useEffect(() => {
    api('GET', '/categories', undefined, { auth: false }).then(setCategories).catch(() => {});
  }, []);

  useEffect(() => {
    setError('');
    const query = new URLSearchParams({ category, q, page: String(page), size: '12' });
    api('GET', `/products?${query}`, undefined, { auth: false })
      .then(setData)
      .catch((e) => setError(e.message));
  }, [category, q, page]);

  const update = (next) => setParams({ category, q, page: '0', ...next }, { replace: true });

  return (
    <>
      <div className="chips">
        <button className={`chip ${category === '' ? 'on' : ''}`} onClick={() => update({ category: '' })}>Tất cả</button>
        {categories.map((c) => (
          <button key={c.id} className={`chip ${category === c.slug ? 'on' : ''}`} onClick={() => update({ category: c.slug })}>{c.name}</button>
        ))}
      </div>
      <form onSubmit={(e) => { e.preventDefault(); update({ q: search.trim() }); }} className="row" style={{ marginBottom: 12 }}>
        <input placeholder="Tìm sản phẩm..." value={search} onChange={(e) => setSearch(e.target.value)} />
      </form>

      {error && <p className="alert alert-error">{error}</p>}
      {!data && !error && <p className="muted">Đang tải...</p>}
      {data && data.items.length === 0 && <p className="muted">Không có sản phẩm phù hợp.</p>}
      {data && (
        <>
          <div className="grid">{data.items.map((p) => <ProductCard key={p.id} product={p} />)}</div>
          {data.totalPages > 1 && (
            <div className="pager">
              <button className="btn" disabled={page <= 0} onClick={() => update({ page: String(page - 1) })}>Trước</button>
              <span>Trang {page + 1} / {data.totalPages}</span>
              <button className="btn" disabled={page + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })}>Sau</button>
            </div>
          )}
        </>
      )}
    </>
  );
}
