import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { useCategories } from '../hooks/useCategories.js';
import ProductCard from '../components/ProductCard.jsx';
import { SEARCH_INPUT_ID } from '../components/layout/Header.jsx';
import Tabs, { tabDomId } from '../components/ui/Tabs.jsx';
import Marquee from '../components/ui/Marquee.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import Button from '../components/ui/Button.jsx';
import Pager from '../components/ui/Pager.jsx';
import { SkeletonCard } from '../components/ui/Skeleton.jsx';
import { CloseIcon, SearchIcon } from '../components/ui/icons.jsx';
import Reveal from '../motion/Reveal.jsx';
import { STAGGER } from '../motion/variants.js';
import Hero from './home/Hero.jsx';
import './home/Home.css';

const PAGE_SIZE = 12;
const SEARCH_DEBOUNCE_MS = 300;
const PERKS = [
  'Miễn phí đổi trả 30 ngày',
  'Thanh toán MoMo',
  'Giao nhanh toàn quốc',
  'Chất liệu tuyển chọn',
  'Thanh toán khi nhận hàng',
  'Thiết kế tối giản',
];

export default function Home() {
  const [params, setParams] = useSearchParams();
  const category = params.get('category') || '';
  const q = params.get('q') || '';
  const page = Math.max(0, parseInt(params.get('page') ?? '0', 10) || 0);
  const focusSearch = params.get('focus') === 'search';

  const categories = useCategories();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [search, setSearch] = useState(q);
  const inputRef = useRef(null);
  const pushedQ = useRef(q);

  useEffect(() => {
    let ignore = false;
    setError('');
    setLoading(true);
    const query = new URLSearchParams({ category, q, page: String(page), size: String(PAGE_SIZE) });
    api('GET', `/products?${query}`, undefined, { auth: false })
      .then((r) => { if (!ignore) setData(r); })
      .catch((e) => { if (!ignore) setError(e.message); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [category, q, page, attempt]);

  /** Writes the filters to the URL (dropping empty ones); any filter change goes back to the first page. */
  const update = useCallback((next) => {
    const merged = { category, q, page: '0', ...next };
    const clean = Object.fromEntries(Object.entries(merged).filter(([k, v]) => v !== '' && !(k === 'page' && v === '0')));
    setParams(clean, { replace: true });
  }, [category, q, setParams]);

  // Keep the box in sync when the URL changes from outside (back button, header links).
  useEffect(() => {
    if (q === pushedQ.current) return;
    pushedQ.current = q;
    setSearch(q);
  }, [q]);

  // Debounced search: typing updates the URL 300ms after the last keystroke.
  useEffect(() => {
    const term = search.trim();
    if (term === q) return undefined;
    const t = setTimeout(() => { pushedQ.current = term; update({ q: term }); }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(t);
  }, [search, q, update]);

  // Header search icon navigates here with ?focus=search.
  useEffect(() => {
    if (!focusSearch) return;
    // Wait for the shell's scroll-to-top on navigation and the first layout before moving to the box.
    // (No cleanup: removing ?focus from the URL below re-runs this effect and must not cancel the timer.)
    setTimeout(() => {
      const input = inputRef.current;
      input?.scrollIntoView({ block: 'center' });
      input?.focus({ preventScroll: true });
    }, 80);
    update({ page: String(page) });
  }, [focusSearch]); // eslint-disable-line react-hooks/exhaustive-deps

  function clearSearch() {
    setSearch('');
    pushedQ.current = '';
    update({ q: '' });
    inputRef.current?.focus();
  }

  function goToPage(next) {
    update({ page: String(next) });
    document.getElementById('collection-title')?.scrollIntoView({ block: 'start' });
  }

  const tabs = [{ value: '', label: 'Tất cả' }, ...categories.map((c) => ({ value: c.slug, label: c.name }))];
  const items = data?.items ?? [];
  const firstLoad = !data && !error;
  const activeCategory = categories.find((c) => c.slug === category)?.name;

  return (
    <>
      <Hero />

      <div className="home-marquee">
        <Marquee items={PERKS} speed={45} />
      </div>

      <section id="products" className="container shop" aria-labelledby="collection-title">
        <div className="shop__head">
          <Reveal>
            <p className="eyebrow">Cửa hàng</p>
            <h2 id="collection-title" className="shop__title">
              {activeCategory ? activeCategory : <>Bộ sưu tập <em>mới</em></>}
            </h2>
          </Reveal>
          <form role="search" className="shop__search" onSubmit={(e) => { e.preventDefault(); pushedQ.current = search.trim(); update({ q: search.trim() }); }}>
            <label htmlFor={SEARCH_INPUT_ID} className="sr-only">Tìm sản phẩm</label>
            <SearchIcon size={18} className="shop__search-icon" />
            <input
              ref={inputRef}
              id={SEARCH_INPUT_ID}
              type="search"
              placeholder="Tìm áo thun, jeans, áo khoác..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Escape' && search) { e.preventDefault(); clearSearch(); } }}
              autoComplete="off"
              enterKeyHint="search"
            />
            {search && (
              <button type="button" className="shop__search-clear" aria-label="Xoá tìm kiếm" onClick={clearSearch}>
                <CloseIcon size={16} />
              </button>
            )}
          </form>
        </div>

        <div className="shop__bar">
          <Tabs
            items={tabs}
            value={category}
            onChange={(value) => update({ category: value })}
            label="Danh mục sản phẩm"
            panelId="product-results"
            idPrefix="shop"
          />
          <p className="shop__count" aria-live="polite">
            {data && !loading && !error && (
              <>{data.totalElements ?? items.length} sản phẩm{q && <> cho “<strong>{q}</strong>”</>}</>
            )}
          </p>
        </div>

        <div
          id="product-results"
          className="shop__results"
          role="tabpanel"
          aria-label="Danh sách sản phẩm"
          aria-labelledby={tabs.some((t) => t.value === category) ? tabDomId('shop', category) : undefined}
          aria-busy={loading}
        >
          {error ? (
            <EmptyState
              tone="danger"
              role="alert"
              title="Không tải được sản phẩm"
              action={<Button variant="dark" onClick={() => setAttempt((a) => a + 1)}>Thử lại</Button>}
            >
              {error}
            </EmptyState>
          ) : firstLoad ? (
            <div className="shop__grid">
              {Array.from({ length: 8 }, (_, i) => <SkeletonCard key={i} />)}
            </div>
          ) : items.length === 0 ? (
            <EmptyState
              icon={<SearchIcon size={26} />}
              title="Không tìm thấy sản phẩm phù hợp"
              action={<Button variant="dark" onClick={() => { setSearch(''); pushedQ.current = ''; update({ q: '', category: '' }); }}>Xem tất cả sản phẩm</Button>}
            >
              {q ? <>Không có kết quả cho “{q}”. Thử từ khoá khác hoặc bỏ bớt bộ lọc.</> : 'Danh mục này chưa có sản phẩm. Hãy quay lại sau nhé.'}
            </EmptyState>
          ) : (
            <ul className={`shop__grid ${loading ? 'is-loading' : ''}`}>
              {items.map((p, i) => (
                <Reveal as="li" key={p.id} delay={(i % 4) * STAGGER}>
                  <ProductCard product={p} eager={i < 4} />
                </Reveal>
              ))}
            </ul>
          )}
        </div>

        {data && !error && <Pager page={page} totalPages={data.totalPages} onChange={goToPage} />}
      </section>
    </>
  );
}
