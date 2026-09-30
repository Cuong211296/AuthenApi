import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client.js';
import Badge from '../../components/ui/Badge.jsx';
import Button from '../../components/ui/Button.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import Tabs from '../../components/ui/Tabs.jsx';
import { useToast } from '../../components/ui/Toast.jsx';
import { AlertIcon, BoxIcon, ChevronIcon, PlusIcon, SearchIcon } from '../../components/ui/icons.jsx';
import { filterProducts, slugify } from '../../utils/admin.js';
import { formatVnd } from '../../utils/money.js';
import AdminPageHeader from './AdminPageHeader.jsx';
import { SearchBox, SkeletonRows, Thumb } from './AdminParts.jsx';
import ProductEditor from './ProductEditor.jsx';

const STATUS_FILTERS = [
  { value: 'all', label: 'Tất cả' },
  { value: 'active', label: 'Đang bán' },
  { value: 'hidden', label: 'Đã ẩn' },
];

export default function AdminProducts() {
  const { toast } = useToast();
  const [list, setList] = useState(null);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [reloads, setReloads] = useState(0);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [newCategory, setNewCategory] = useState('');
  const [addingCategory, setAddingCategory] = useState(false);
  const [drawer, setDrawer] = useState({ open: false, target: null, key: 0 });

  useEffect(() => {
    let ignore = false;
    Promise.all([
      api('GET', '/admin/products?size=100'),
      api('GET', '/categories', undefined, { auth: false }),
    ]).then(([l, c]) => {
      if (ignore) return;
      setList(l); setCategories(c); setError('');
    }).catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [attempt]);

  // Refresh only the product list after the editor saved or hid something (no loading flash).
  useEffect(() => {
    if (reloads === 0) return undefined;
    let ignore = false;
    api('GET', '/admin/products?size=100').then((l) => { if (!ignore) setList(l); }).catch(() => {});
    return () => { ignore = true; };
  }, [reloads]);

  const items = list?.items;
  const counts = useMemo(() => ({
    all: items?.length ?? 0,
    active: items?.filter((p) => p.active).length ?? 0,
    hidden: items?.filter((p) => !p.active).length ?? 0,
  }), [items]);
  const visible = useMemo(() => {
    if (!items) return [];
    const byStatus = statusFilter === 'all' ? items : items.filter((p) => p.active === (statusFilter === 'active'));
    return filterProducts(byStatus, query);
  }, [items, statusFilter, query]);

  const openEditor = (target) => setDrawer((d) => ({ open: true, target, key: d.key + 1 }));
  const closeEditor = () => setDrawer((d) => ({ ...d, open: false }));

  async function createCategory(e) {
    e.preventDefault();
    const name = newCategory.trim();
    if (!name) return;
    setAddingCategory(true);
    try {
      await api('POST', '/admin/categories', { name, slug: slugify(name) });
      setNewCategory('');
      setCategories(await api('GET', '/categories', undefined, { auth: false }));
      toast('Đã thêm danh mục', { tone: 'success' });
    } catch (err) {
      toast(err.message, { tone: 'danger' });
    } finally {
      setAddingCategory(false);
    }
  }

  const filtering = query.trim() !== '' || statusFilter !== 'all';
  const tabItems = STATUS_FILTERS.map((f) => ({ value: f.value, label: items ? `${f.label} (${counts[f.value]})` : f.label }));

  let body;
  if (error && !list) {
    body = (
      <EmptyState
        role="alert"
        tone="danger"
        icon={<AlertIcon size={26} />}
        title="Không tải được sản phẩm"
        action={<Button variant="dark" onClick={() => { setError(''); setAttempt((n) => n + 1); }}>Thử lại</Button>}
      >
        {error}
      </EmptyState>
    );
  } else if (!list) {
    body = <div className="ad-card"><SkeletonRows label="Đang tải sản phẩm" /></div>;
  } else if (items.length === 0) {
    body = (
      <EmptyState
        icon={<BoxIcon size={26} />}
        title="Chưa có sản phẩm"
        action={<Button iconLeft={<PlusIcon size={18} />} onClick={() => openEditor({ isNew: true })}>Thêm sản phẩm</Button>}
      >
        Tạo sản phẩm đầu tiên để bắt đầu bán hàng.
      </EmptyState>
    );
  } else if (visible.length === 0) {
    body = (
      <EmptyState
        icon={<SearchIcon size={26} />}
        title="Không có sản phẩm phù hợp"
        action={<Button variant="dark" onClick={() => { setQuery(''); setStatusFilter('all'); }}>Xoá bộ lọc</Button>}
      >
        Thử từ khoá khác hoặc bỏ bộ lọc trạng thái.
      </EmptyState>
    );
  } else {
    body = (
      <div className="ad-card">
        <table className="ad-table ad-table--cards">
          <thead>
            <tr>
              <th scope="col">Sản phẩm</th>
              <th scope="col">Danh mục</th>
              <th scope="col" className="is-right">Giá gốc</th>
              <th scope="col">Trạng thái</th>
              <th scope="col"><span className="sr-only">Mở</span></th>
            </tr>
          </thead>
          <tbody>
            {visible.map((p) => (
              <tr key={p.id} className="ad-prow" onClick={() => openEditor({ id: p.id })} style={{ cursor: 'pointer' }}>
                <td>
                  <div className="ad-namecell">
                    <Thumb src={p.imageUrl} name={p.name} />
                    <div className="ad-namecell__text">
                      <button type="button" className="ad-linkbtn" aria-label={`Sửa sản phẩm ${p.name}`}>{p.name}</button>
                      <span className="ad-muted">{p.slug}</span>
                    </div>
                  </div>
                </td>
                <td className="ad-prow__cat ad-muted" data-label="Danh mục">{p.categoryName || '—'}</td>
                <td className="is-right ad-num ad-strong ad-prow__meta" data-label="Giá gốc">
                  <span>{formatVnd(p.basePrice)}</span>
                  <Badge tone={p.active ? 'success' : 'neutral'} dot className="ad-only-mobile">{p.active ? 'Đang bán' : 'Đã ẩn'}</Badge>
                </td>
                <td data-label="Trạng thái" className="ad-prow__status">
                  <Badge tone={p.active ? 'success' : 'neutral'} dot>{p.active ? 'Đang bán' : 'Đã ẩn'}</Badge>
                </td>
                <td className="ad-prow__go" aria-hidden="true"><ChevronIcon direction="right" size={18} style={{ color: 'var(--muted)' }} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <>
      <AdminPageHeader
        title="Sản phẩm"
        description="Quản lý danh mục hàng, giá và tồn kho theo từng biến thể."
        actions={<Button iconLeft={<PlusIcon size={18} />} onClick={() => openEditor({ isNew: true })}>Thêm sản phẩm</Button>}
      />

      <div className="ad-toolbar">
        <SearchBox value={query} onChange={setQuery} label="Tìm sản phẩm" placeholder="Tìm sản phẩm" />
        <Tabs items={tabItems} value={statusFilter} onChange={setStatusFilter} label="Lọc theo trạng thái" />
        <form className="ad-inline-form" onSubmit={createCategory}>
          <label className="sr-only" htmlFor="ad-new-category">Danh mục mới</label>
          <input id="ad-new-category" placeholder="Danh mục mới" value={newCategory} onChange={(e) => setNewCategory(e.target.value)} required />
          <Button type="submit" variant="ghost" loading={addingCategory}>Thêm danh mục</Button>
        </form>
      </div>
      {items && (
        <p className="ad-muted" style={{ marginBottom: 12 }} role="status">
          {filtering ? `${visible.length} / ${items.length} sản phẩm` : `${items.length} sản phẩm`}
          {list.totalElements > items.length ? ` (hiển thị ${items.length} trên ${list.totalElements})` : ''}
        </p>
      )}

      {body}

      {drawer.target && (
        <ProductEditor
          key={drawer.key}
          open={drawer.open}
          target={drawer.target}
          categories={categories}
          onClose={closeEditor}
          onChanged={() => setReloads((n) => n + 1)}
        />
      )}
    </>
  );
}
