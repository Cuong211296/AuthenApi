import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import { formatVnd } from '../../utils/money.js';

const EMPTY_PRODUCT = { name: '', slug: '', description: '', categoryId: '', basePrice: 0, imageUrl: '', active: true };
const EMPTY_VARIANT = { size: '', color: '', sku: '', stock: 0, price: '' };

// VariantResponse.price is the effective price: show an override only when it differs from basePrice.
const withOverrides = (p) => ({
  ...p,
  variants: (p.variants || []).map((v) => ({ ...v, priceOverride: Number(v.price) !== Number(p.basePrice) ? String(v.price) : '' })),
});

const slugify = (s) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd')
  .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

export default function AdminProducts() {
  const [list, setList] = useState(null);
  const [categories, setCategories] = useState([]);
  const [editing, setEditing] = useState(null);     // product detail or {new: true, ...EMPTY_PRODUCT}
  const [variant, setVariant] = useState(EMPTY_VARIANT);
  const [newCategory, setNewCategory] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const guard = async (fn, okMessage) => {
    setError(''); setMessage('');
    try { await fn(); if (okMessage) setMessage(okMessage); } catch (e) { setError(e.message); }
  };
  const loadList = () => api('GET', '/admin/products?size=100').then(setList);
  const loadCategories = () => api('GET', '/categories', undefined, { auth: false }).then(setCategories);
  useEffect(() => {
    let ignore = false;
    Promise.all([
      api('GET', '/admin/products?size=100'),
      api('GET', '/categories', undefined, { auth: false }),
    ]).then(([l, c]) => {
      if (ignore) return;
      setList(l); setCategories(c);
    }).catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, []);

  const open = (id) => guard(async () => setEditing(withOverrides(await api('GET', `/admin/products/${id}`))));
  const productBody = (p) => ({
    name: p.name, slug: p.slug, description: p.description || '', categoryId: p.categoryId || p.category?.id || '',
    basePrice: Number(p.basePrice), imageUrl: p.imageUrl || '', active: p.active,
  });

  const saveProduct = (e) => {
    e.preventDefault();
    return guard(async () => {
      const body = productBody(editing);
      const saved = editing.isNew
        ? await api('POST', '/admin/products', body)
        : await api('PUT', `/admin/products/${editing.id}`, body);
      setEditing(withOverrides(saved));
      await loadList();
    }, 'Đã lưu sản phẩm');
  };

  const deactivate = (id) => window.confirm('Ẩn sản phẩm này khỏi cửa hàng?') && guard(async () => {
    await api('DELETE', `/admin/products/${id}`);
    setEditing(null);
    await loadList();
  }, 'Đã ẩn sản phẩm');

  const addVariant = (e) => {
    e.preventDefault();
    return guard(async () => {
      await api('POST', `/admin/products/${editing.id}/variants`, {
        ...variant, stock: Number(variant.stock), price: variant.price === '' ? null : Number(variant.price),
      });
      setVariant(EMPTY_VARIANT);
      setEditing(withOverrides(await api('GET', `/admin/products/${editing.id}`)));
    }, 'Đã thêm biến thể');
  };

  const saveVariant = (v) => guard(async () => {
    await api('PUT', `/admin/variants/${v.id}`, {
      size: v.size, color: v.color, sku: v.sku, stock: Number(v.stock), price: v.priceOverride === '' || v.priceOverride == null ? null : Number(v.priceOverride), active: v.active,
    });
    setEditing(withOverrides(await api('GET', `/admin/products/${editing.id}`)));
  }, 'Đã lưu biến thể');

  const createCategory = (e) => {
    e.preventDefault();
    return guard(async () => {
      await api('POST', '/admin/categories', { name: newCategory.trim(), slug: slugify(newCategory) });
      setNewCategory('');
      await loadCategories();
    }, 'Đã thêm danh mục');
  };

  const setField = (key) => (e) => setEditing({ ...editing, [key]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const patchVariant = (id, patch) => setEditing({ ...editing, variants: editing.variants.map((v) => (v.id === id ? { ...v, ...patch } : v)) });

  return (
    <div className="row">
      <div className="card">
        <h1>Sản phẩm</h1>
        {error && <p className="alert alert-error">{error}</p>}
        {message && <p className="alert alert-ok">{message}</p>}
        <p><button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY_PRODUCT, isNew: true, variants: [] })}>+ Thêm sản phẩm</button></p>
        <form className="row" onSubmit={createCategory}>
          <input placeholder="Danh mục mới" value={newCategory} onChange={(e) => setNewCategory(e.target.value)} required />
          <button className="btn">Thêm danh mục</button>
        </form>
        {!list ? <p className="muted">Đang tải...</p> : (
          <table>
            <tbody>
              {list.items.map((p) => (
                <tr key={p.id} onClick={() => open(p.id)} style={{ cursor: 'pointer' }}>
                  <td>{p.name}<div className="muted">{p.slug}</div></td>
                  <td>{formatVnd(p.basePrice)}</td>
                  <td>{p.active ? <span className="badge">Đang bán</span> : <span className="badge">Đã ẩn</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {editing && (
        <div className="card">
          <h2>{editing.isNew ? 'Sản phẩm mới' : 'Sửa sản phẩm'}</h2>
          <form className="form" onSubmit={saveProduct}>
            <label>Tên<input value={editing.name} required onChange={(e) => setEditing({ ...editing, name: e.target.value, slug: editing.isNew ? slugify(e.target.value) : editing.slug })} /></label>
            <label>Slug<input value={editing.slug} required pattern="[a-z0-9]+(-[a-z0-9]+)*" onChange={setField('slug')} /></label>
            <label>Danh mục
              <select value={editing.categoryId ?? editing.category?.id ?? ''} onChange={setField('categoryId')}>
                <option value="">(không)</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
            <label>Giá gốc (₫)<input type="number" min="0" required value={editing.basePrice} onChange={setField('basePrice')} /></label>
            <label>Link ảnh<input value={editing.imageUrl || ''} onChange={setField('imageUrl')} /></label>
            <label>Mô tả<textarea rows={3} value={editing.description || ''} onChange={setField('description')} /></label>
            <label><input type="checkbox" checked={editing.active} onChange={setField('active')} /> Đang bán</label>
            <div>
              <button className="btn btn-primary">Lưu</button>{' '}
              {!editing.isNew && editing.active && <button type="button" className="btn btn-danger" onClick={() => deactivate(editing.id)}>Ẩn sản phẩm</button>}
            </div>
          </form>

          {!editing.isNew && (
            <>
              <h3>Biến thể (size / màu / kho)</h3>
              <table>
                <thead><tr><th>Size</th><th>Màu</th><th>SKU</th><th>Kho</th><th>Giá riêng</th><th>Bán</th><th /></tr></thead>
                <tbody>
                  {editing.variants.map((v) => (
                    <tr key={v.id}>
                      <td>{v.size}</td><td>{v.color}</td><td>{v.sku}</td>
                      <td><input type="number" min="0" style={{ width: 70 }} value={v.stock} onChange={(e) => patchVariant(v.id, { stock: e.target.value })} /></td>
                      <td><input type="number" min="0" style={{ width: 100 }} placeholder="theo giá gốc" value={v.priceOverride ?? ''} onChange={(e) => patchVariant(v.id, { priceOverride: e.target.value })} /></td>
                      <td><input type="checkbox" checked={v.active} onChange={(e) => patchVariant(v.id, { active: e.target.checked })} /></td>
                      <td><button className="btn" onClick={() => saveVariant(v)}>Lưu</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <form className="row" onSubmit={addVariant}>
                <input placeholder="Size" required value={variant.size} onChange={(e) => setVariant({ ...variant, size: e.target.value })} />
                <input placeholder="Màu" required value={variant.color} onChange={(e) => setVariant({ ...variant, color: e.target.value })} />
                <input placeholder="SKU" required value={variant.sku} onChange={(e) => setVariant({ ...variant, sku: e.target.value })} />
                <input type="number" min="0" placeholder="Kho" required value={variant.stock} onChange={(e) => setVariant({ ...variant, stock: e.target.value })} />
                <input type="number" min="0" placeholder="Giá riêng (tuỳ chọn)" value={variant.price} onChange={(e) => setVariant({ ...variant, price: e.target.value })} />
                <button className="btn">+ Thêm biến thể</button>
              </form>
            </>
          )}
        </div>
      )}
    </div>
  );
}
