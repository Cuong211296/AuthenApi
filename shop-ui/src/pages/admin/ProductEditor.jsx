import { useEffect, useRef, useState } from 'react';
import { api, uploadProductImage } from '../../api/client.js';
import Button from '../../components/ui/Button.jsx';
import Drawer from '../../components/ui/Drawer.jsx';
import Field from '../../components/ui/Field.jsx';
import Switch from '../../components/ui/Switch.jsx';
import { Skeleton } from '../../components/ui/Skeleton.jsx';
import { useToast } from '../../components/ui/Toast.jsx';
import { PlusIcon, UploadIcon } from '../../components/ui/icons.jsx';
import { costPriceError, costPriceValue, countError, editorSnapshot, isEditorDirty, mergeVariants, optionalCountError, overrideOf, slugify, variantErrors, weightGramsError, weightGramsValue } from '../../utils/admin.js';
import { imageFileError } from '../../utils/image.js';
import { Thumb } from './AdminParts.jsx';

const EMPTY_PRODUCT = { name: '', slug: '', description: '', categoryId: '', basePrice: '', costPrice: '', weightGrams: '', imageUrl: '', active: true };
const EMPTY_VARIANT = { size: '', color: '', sku: '', stock: '', price: '' };
const KEEP_LABEL = { hide: 'Giữ lại', discard: 'Tiếp tục chỉnh sửa' };
const SLUG_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

// VariantResponse.price is the effective price: show an override only when it differs from basePrice.
// `baseline` remembers the saved base price so overrides stay correct while the base price field has unsaved edits.
const withOverrides = (p) => ({
  ...p,
  baseline: p.basePrice,
  variants: (p.variants || []).map((v) => ({ ...v, priceOverride: overrideOf(v, p.basePrice) })),
});

const productBody = (p) => ({
  name: p.name,
  slug: p.slug,
  description: p.description || '',
  categoryId: p.categoryId ?? p.category?.id ?? '', // '' clears the category (the API treats blank as none)
  basePrice: Number(p.basePrice),
  costPrice: costPriceValue(p.costPrice), // empty -> null (cost unknown); admin only
  weightGrams: weightGramsValue(p.weightGrams), // empty -> null (server default 300 g); a loaded weight is sent back unchanged
  imageUrl: p.imageUrl || '',
  active: p.active,
});

function validateProduct(p) {
  const errs = {};
  if (!p.name.trim()) errs.name = 'Nhập tên sản phẩm';
  if (!p.slug) errs.slug = 'Nhập slug';
  else if (!SLUG_RE.test(p.slug)) errs.slug = 'Slug chỉ gồm chữ thường, số và dấu gạch ngang';
  const price = countError(p.basePrice, 'Giá gốc');
  if (price) errs.basePrice = price;
  const cost = costPriceError(p.costPrice);
  if (cost) errs.costPrice = cost;
  const weight = weightGramsError(p.weightGrams);
  if (weight) errs.weightGrams = weight;
  return errs;
}

function focusFirstInvalid(root) {
  requestAnimationFrame(() => root?.querySelector('[aria-invalid="true"]')?.focus());
}

/**
 * Slide-over editor for one product ({ id } to edit, { isNew: true } to create).
 * Loads the detail itself (ignore flag), keeps unsaved variant edits when other parts are saved,
 * and tells the page through `onChanged` when the list should be refreshed.
 */
export default function ProductEditor({ open, target, categories, onClose, onChanged }) {
  const { toast } = useToast();
  const [editing, setEditing] = useState(target.isNew ? { ...EMPTY_PRODUCT, isNew: true, variants: [], baseline: 0 } : null);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [errs, setErrs] = useState({});
  const [busy, setBusy] = useState(''); // 'save' | 'hide' | 'add' | variant id
  const [confirm, setConfirm] = useState(null); // null | 'hide' | 'discard' (only one inline confirm at a time)
  const [baseline, setBaseline] = useState(() => (target.isNew ? editorSnapshot({ ...EMPTY_PRODUCT, variants: [] }) : null));
  const keepRef = useRef(null);
  const hideBtnRef = useRef(null);
  const closeBtnRef = useRef(null);
  const returnFocus = useRef(null);
  const prevConfirm = useRef(null);
  const [variant, setVariant] = useState(EMPTY_VARIANT);
  const [addErrs, setAddErrs] = useState({});
  const [varErrs, setVarErrs] = useState({});
  const [varError, setVarError] = useState('');
  const bodyRef = useRef(null);
  const [attempt, setAttempt] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [dragging, setDragging] = useState(false);
  const fileRef = useRef(null);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []); // StrictMode remounts effects

  useEffect(() => {
    if (!target.id) return undefined;
    let ignore = false;
    setLoadError('');
    api('GET', `/admin/products/${target.id}`)
      .then((p) => { if (!ignore) { const loaded = withOverrides(p); setEditing(loaded); setBaseline(editorSnapshot(loaded)); } })
      .catch((e) => { if (!ignore) setLoadError(e.message); });
    return () => { ignore = true; };
  }, [target.id, attempt]);

  const setField = (key) => (e) => setEditing((p) => ({ ...p, [key]: e.target.value }));
  const patchVariant = (id, patch) => {
    setEditing((p) => ({ ...p, variants: p.variants.map((v) => (v.id === id ? { ...v, ...patch } : v)) }));
    setVarErrs((m) => ({ ...m, [id]: undefined }));
  };

  // Uploads one image, then stores the returned URL in the existing imageUrl field (so the dirty guard sees it).
  async function handleFile(file) {
    if (!file || uploading) return;
    const problem = imageFileError(file);
    setUploadError(problem);
    if (problem) return;
    setUploading(true);
    try {
      const url = await uploadProductImage(file);
      if (mounted.current) setEditing((p) => ({ ...p, imageUrl: url }));
    } catch (err) {
      if (mounted.current) setUploadError(err.message || 'Tải ảnh lên thất bại, vui lòng thử lại');
    } finally {
      if (mounted.current) setUploading(false);
    }
  }

  const onPick = (e) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // lets the same file be picked again after an error
    handleFile(file);
  };
  const onDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    handleFile(e.dataTransfer?.files?.[0]);
  };

  async function saveProduct(e) {
    e.preventDefault();
    const found = validateProduct(editing);
    setErrs(found);
    if (Object.keys(found).length) { focusFirstInvalid(bodyRef.current); return; }
    setError('');
    setBusy('save');
    try {
      const body = productBody(editing);
      const saved = editing.isNew ? await api('POST', '/admin/products', body) : await api('PUT', `/admin/products/${editing.id}`, body);
      const fresh = withOverrides(saved);
      setEditing((prev) => ({ ...fresh, variants: prev.isNew ? fresh.variants : prev.variants }));
      setBaseline((b) => ({ ...b, product: editorSnapshot(fresh).product }));
      toast('Đã lưu sản phẩm', { tone: 'success' });
      onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy('');
    }
  }

  async function deactivate() {
    setError('');
    setBusy('hide');
    try {
      await api('DELETE', `/admin/products/${editing.id}`);
      toast('Đã ẩn sản phẩm', { tone: 'success' });
      onChanged();
      onClose();
    } catch (err) {
      setError(err.message);
      setConfirm(null);
    } finally {
      setBusy('');
    }
  }

  // Reloads variants after a successful write. A failed reload must not look like a failed write (a retry would
  // hit a duplicate SKU), so it only warns and keeps the local state.
  const refetchVariants = async (refreshIds, savedLocal) => {
    try {
      const fresh = await api('GET', `/admin/products/${editing.id}`);
      const serverVariants = fresh.variants || [];
      setEditing((prev) => ({ ...prev, baseline: fresh.basePrice, variants: mergeVariants(prev.variants, serverVariants, fresh.basePrice, refreshIds) }));
      setBaseline((b) => {
        const variants = { ...b.variants };
        for (const sv of serverVariants) {
          if (refreshIds.includes(sv.id) || !variants[sv.id]) variants[sv.id] = editorSnapshot({ variants: [{ ...sv, priceOverride: overrideOf(sv, fresh.basePrice) }] }).variants[sv.id];
        }
        return { ...b, variants };
      });
    } catch {
      if (savedLocal) setBaseline((b) => ({ ...b, variants: { ...b.variants, ...editorSnapshot({ variants: [savedLocal] }).variants } }));
      toast('Đã lưu, nhưng chưa tải lại được danh sách biến thể', { tone: 'info' });
      return false;
    }
    return true;
  };

  async function addVariant(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const found = {};
    for (const key of ['size', 'color', 'sku']) if (!variant[key].trim()) found[key] = 'Bắt buộc';
    const stock = countError(variant.stock, 'Tồn kho');
    if (stock) found.stock = stock;
    const price = optionalCountError(variant.price, 'Giá riêng');
    if (price) found.price = price;
    setAddErrs(found);
    if (Object.keys(found).length) { focusFirstInvalid(form); return; }
    setVarError('');
    setBusy('add');
    try {
      await api('POST', `/admin/products/${editing.id}/variants`, {
        ...variant, stock: Number(variant.stock), price: variant.price === '' ? null : Number(variant.price),
      });
      setVariant(EMPTY_VARIANT);
      if (await refetchVariants([])) toast('Đã thêm biến thể', { tone: 'success' });
    } catch (err) {
      setVarError(err.message);
    } finally {
      setBusy('');
    }
  }

  async function saveVariant(v) {
    const found = variantErrors(v);
    setVarErrs((m) => ({ ...m, [v.id]: found }));
    if (Object.keys(found).length) { focusFirstInvalid(bodyRef.current?.querySelector(`[data-variant="${v.id}"]`)); return; }
    setVarError('');
    setBusy(v.id);
    try {
      await api('PUT', `/admin/variants/${v.id}`, {
        size: v.size, color: v.color, sku: v.sku, stock: Number(v.stock),
        price: v.priceOverride === '' || v.priceOverride == null ? null : Number(v.priceOverride), active: v.active,
      });
      if (await refetchVariants([v.id], v)) toast('Đã lưu biến thể', { tone: 'success' });
    } catch (err) {
      setVarError(err.message);
    } finally {
      setBusy('');
    }
  }

  const dirty = isEditorDirty(baseline, editing);
  const busyWriting = busy === 'save' || busy === 'hide';

  // Every close path (overlay, Esc, X, "Đóng") comes through here.
  const requestClose = () => {
    if (busyWriting) return;
    if (confirm) { setConfirm(null); return; } // Esc/overlay dismisses the open confirm first
    if (dirty) { openConfirm('discard'); return; }
    onClose();
  };
  function openConfirm(kind) {
    const active = document.activeElement;
    returnFocus.current = bodyRef.current?.contains(active) ? active : null;
    setConfirm(kind);
  }

  // Focus: into the safe button when a confirm opens, back to where the user was when it is dismissed.
  useEffect(() => {
    if (confirm) {
      keepRef.current?.focus();
    } else if (prevConfirm.current) {
      const back = returnFocus.current?.isConnected ? returnFocus.current : (prevConfirm.current === 'hide' ? hideBtnRef.current : closeBtnRef.current);
      back?.focus();
      returnFocus.current = null;
    }
    prevConfirm.current = confirm;
  }, [confirm]);

  const isNew = Boolean(editing?.isNew);
  const title = isNew ? 'Sản phẩm mới' : 'Sửa sản phẩm';
  const canHide = editing && !isNew && editing.active;

  const footer = editing && (
    confirm ? (
      <div className="ad-confirm" role="alertdialog" aria-labelledby="ad-confirm-msg">
        <p id="ad-confirm-msg">{confirm === 'hide' ? 'Ẩn sản phẩm này khỏi cửa hàng?' : 'Bỏ các thay đổi chưa lưu?'}</p>
        <Button ref={keepRef} variant="ghost" size="sm" onClick={() => setConfirm(null)} disabled={busy === 'hide'}>{KEEP_LABEL[confirm]}</Button>
        {confirm === 'hide' ? (
          <Button variant="danger" size="sm" className="ad-solid-danger" loading={busy === 'hide'} onClick={deactivate}>Ẩn sản phẩm</Button>
        ) : (
          <Button variant="danger" size="sm" className="ad-solid-danger" onClick={onClose}>Bỏ thay đổi</Button>
        )}
      </div>
    ) : (
      <div className="ad-foot">
        {canHide && <Button ref={hideBtnRef} variant="danger" onClick={() => openConfirm('hide')}>Ẩn sản phẩm</Button>}
        <span className="ad-foot__spacer" />
        <Button ref={closeBtnRef} variant="ghost" onClick={requestClose}>Đóng</Button>
        <Button type="submit" form="ad-product-form" loading={busy === 'save'}>{isNew ? 'Tạo sản phẩm' : 'Lưu'}</Button>
      </div>
    )
  );

  return (
    <Drawer open={open} onClose={requestClose} title={editing ? title : 'Sản phẩm'} footer={footer} width={760}>
      <div ref={bodyRef}>
        {loadError && (
          <div className="ad-error" role="alert">
            {loadError}{' '}
            <button type="button" className="ad-linkbtn" onClick={() => setAttempt((n) => n + 1)}>Thử lại</button>
          </div>
        )}
        {!editing && !loadError && (
          <div className="ad-editor-skel" role="status" aria-label="Đang tải sản phẩm">
            <Skeleton height={50} radius={12} />
            <Skeleton height={50} radius={12} />
            <Skeleton height={50} radius={12} />
            <Skeleton height={120} radius={12} />
          </div>
        )}
        {editing && (
          <div className="ad-editor">
            <form id="ad-product-form" className="ad-section" onSubmit={saveProduct} noValidate>
              <h3 className="ad-section__title">Thông tin</h3>
              {error && <p className="ad-error" role="alert" style={{ margin: 0 }}>{error}</p>}
              <Field
                label="Tên"
                value={editing.name}
                error={errs.name}
                autoComplete="off"
                onChange={(e) => setEditing((p) => ({ ...p, name: e.target.value, slug: p.isNew ? slugify(e.target.value) : p.slug }))}
              />
              <div className="ad-grid2">
                <Field label="Slug" value={editing.slug} error={errs.slug} onChange={setField('slug')} autoComplete="off" />
                <Field as="select" label="Danh mục" value={editing.categoryId ?? editing.category?.id ?? ''} onChange={setField('categoryId')}>
                  <option value="">(không)</option>
                  {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </Field>
              </div>
              <div className="ad-grid2">
                <Field
                  label="Giá gốc (₫)"
                  type="number"
                  inputMode="numeric"
                  min="0"
                  value={editing.basePrice}
                  error={errs.basePrice}
                  onChange={setField('basePrice')}
                />
                <Field
                  label="Giá vốn (tuỳ chọn)"
                  type="number"
                  inputMode="numeric"
                  min="0"
                  value={editing.costPrice ?? ''}
                  error={errs.costPrice}
                  hint="Dùng để tính lợi nhuận, khách hàng không nhìn thấy"
                  onChange={setField('costPrice')}
                />
              </div>
              <Field
                label="Cân nặng (g)"
                optional
                type="number"
                inputMode="numeric"
                min="1"
                max="50000"
                value={editing.weightGrams ?? ''}
                error={errs.weightGrams}
                hint="Để trống để dùng mặc định 300 g. Dùng để tính phí vận chuyển GHTK."
                onChange={setField('weightGrams')}
              />
              <div className="ad-imgrow">
                <Field label="Link ảnh" optional value={editing.imageUrl || ''} onChange={setField('imageUrl')} placeholder="https://" autoComplete="off" />
                <Thumb key={editing.imageUrl || 'none'} src={editing.imageUrl} name={editing.name} size={92} />
              </div>
              <div
                className={`ad-upload ${dragging ? 'is-drag' : ''}`}
                onDragOver={(e) => { e.preventDefault(); if (!dragging) setDragging(true); }}
                onDragLeave={() => setDragging(false)}
                onDrop={onDrop}
              >
                <input
                  ref={fileRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="sr-only"
                  tabIndex={-1}
                  aria-label="Chọn ảnh sản phẩm"
                  data-testid="image-file"
                  onChange={onPick}
                />
                <Button
                  variant="ghost"
                  size="sm"
                  iconLeft={<UploadIcon size={16} />}
                  loading={uploading}
                  onClick={() => fileRef.current?.click()}
                >
                  Tải ảnh lên
                </Button>
                <span className="ad-upload__hint" role="status">
                  {uploading ? 'Đang tải ảnh lên…' : 'hoặc kéo thả ảnh vào đây. JPEG, PNG, WebP, tối đa 5 MB.'}
                </span>
                {uploadError && <p className="ad-error ad-upload__err" role="alert">{uploadError}</p>}
              </div>
              <Field as="textarea" label="Mô tả" optional rows={3} value={editing.description || ''} onChange={setField('description')} />
              <div className="ad-activebox">
                <div>
                  <strong>Đang bán</strong>
                  <p>{editing.active ? 'Hiển thị trong cửa hàng.' : 'Đang ẩn khỏi cửa hàng.'}</p>
                </div>
                <Switch checked={editing.active} onChange={(on) => setEditing((p) => ({ ...p, active: on }))} label="Đang bán" hideLabel />
              </div>
            </form>

            {!isNew && (
              <section className="ad-section" aria-labelledby="ad-variants-title">
                <h3 className="ad-section__title" id="ad-variants-title">Biến thể (size / màu / kho)</h3>
                {varError && <p className="ad-error" role="alert" style={{ margin: 0 }}>{varError}</p>}
                {editing.variants.length === 0 ? (
                  <p className="ad-muted">Chưa có biến thể. Thêm biến thể bên dưới để sản phẩm có thể được mua.</p>
                ) : (
                  <>
                    <div className="ad-varhead" aria-hidden="true">
                      <span>Size</span><span>Màu</span><span>SKU</span><span>Kho</span><span>Giá riêng</span><span>Bán</span><span />
                    </div>
                    <ul className="ad-vars">
                      {editing.variants.map((v) => {
                        const ve = varErrs[v.id] || {};
                        const name = `${v.size} ${v.color}`;
                        return (
                          <li key={v.id} className={`ad-var ${v.active ? '' : 'is-off'}`} data-variant={v.id}>
                            <div className="ad-var__cell ad-var__id ad-strong">{v.size}</div>
                            <div className="ad-var__cell ad-var__id">{v.color}</div>
                            <div className="ad-var__cell ad-var__sku">{v.sku}</div>
                            <div className="ad-var__cell">
                              <span className="ad-var__lbl">Kho</span>
                              <input
                                type="number" min="0" inputMode="numeric" aria-label={`Tồn kho ${name}`}
                                value={v.stock} aria-invalid={ve.stock ? true : undefined}
                                aria-describedby={ve.stock || ve.price ? `err-${v.id}` : undefined}
                                onChange={(e) => patchVariant(v.id, { stock: e.target.value })}
                              />
                            </div>
                            <div className="ad-var__cell">
                              <span className="ad-var__lbl">Giá riêng</span>
                              <input
                                type="number" min="0" inputMode="numeric" aria-label={`Giá riêng ${name}`} placeholder="theo giá gốc"
                                value={v.priceOverride ?? ''} aria-invalid={ve.price ? true : undefined}
                                aria-describedby={ve.stock || ve.price ? `err-${v.id}` : undefined}
                                onChange={(e) => patchVariant(v.id, { priceOverride: e.target.value })}
                              />
                            </div>
                            <div className="ad-var__cell">
                              <span className="ad-var__lbl">Bán</span>
                              <Switch checked={v.active} onChange={(on) => patchVariant(v.id, { active: on })} label={`Bán ${name}`} hideLabel />
                            </div>
                            <div className="ad-var__cell ad-var__act">
                              <Button size="sm" variant="ghost" loading={busy === v.id} disabled={busy !== '' && busy !== v.id} onClick={() => saveVariant(v)} aria-label={`Lưu biến thể ${name}`}>Lưu</Button>
                            </div>
                            {(ve.stock || ve.price) && <p className="ad-var__err" id={`err-${v.id}`} role="alert">{ve.stock || ve.price}</p>}
                          </li>
                        );
                      })}
                    </ul>
                  </>
                )}

                <form className="ad-addvar" onSubmit={addVariant} noValidate aria-label="Thêm biến thể">
                  <div className="ad-addvar__grid">
                    <Field label="Size" value={variant.size} error={addErrs.size} onChange={(e) => setVariant({ ...variant, size: e.target.value })} autoComplete="off" />
                    <Field label="Màu" value={variant.color} error={addErrs.color} onChange={(e) => setVariant({ ...variant, color: e.target.value })} autoComplete="off" />
                    <Field label="SKU" value={variant.sku} error={addErrs.sku} onChange={(e) => setVariant({ ...variant, sku: e.target.value })} autoComplete="off" />
                    <Field label="Kho" type="number" min="0" inputMode="numeric" value={variant.stock} error={addErrs.stock} onChange={(e) => setVariant({ ...variant, stock: e.target.value })} />
                    <Field label="Giá riêng" optional type="number" min="0" inputMode="numeric" value={variant.price} error={addErrs.price} placeholder="theo giá gốc" onChange={(e) => setVariant({ ...variant, price: e.target.value })} />
                  </div>
                  <div className="ad-addvar__foot">
                    <Button type="submit" variant="dark" size="sm" iconLeft={<PlusIcon size={16} />} loading={busy === 'add'}>Thêm biến thể</Button>
                  </div>
                </form>
              </section>
            )}
          </div>
        )}
      </div>
    </Drawer>
  );
}
