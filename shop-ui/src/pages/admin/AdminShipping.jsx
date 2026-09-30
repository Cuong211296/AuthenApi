import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client.js';
import Button from '../../components/ui/Button.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import Field from '../../components/ui/Field.jsx';
import { useToast } from '../../components/ui/Toast.jsx';
import { AlertIcon, PlusIcon, SearchIcon, TruckIcon } from '../../components/ui/icons.jsx';
import { countError, filterRates, parseCount } from '../../utils/admin.js';
import { formatVnd } from '../../utils/money.js';
import AdminPageHeader from './AdminPageHeader.jsx';
import { SearchBox, SkeletonRows } from './AdminParts.jsx';

const DEFAULT_FEE = '35000';

export default function AdminShipping() {
  const { toast } = useToast();
  const [rates, setRates] = useState(null);
  const [fees, setFees] = useState({});          // draft input per rate id (strings)
  const [rowErrors, setRowErrors] = useState({}); // validation message per rate id
  const [saving, setSaving] = useState('');
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [query, setQuery] = useState('');
  const [newRate, setNewRate] = useState({ province: '', fee: DEFAULT_FEE });
  const [newErrors, setNewErrors] = useState({});
  const [creating, setCreating] = useState(false);

  const apply = (list) => {
    setRates(list);
    setFees(Object.fromEntries(list.map((r) => [r.id, String(r.fee)])));
    setRowErrors({});
  };

  useEffect(() => {
    let ignore = false;
    api('GET', '/admin/shipping-rates')
      .then((list) => { if (!ignore) { apply(list); setError(''); } })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [attempt]);

  const visible = useMemo(() => (rates ? filterRates(rates, query) : []), [rates, query]);

  async function save(rate) {
    const message = countError(fees[rate.id], 'Phí vận chuyển');
    setRowErrors((m) => ({ ...m, [rate.id]: message }));
    if (message) { document.getElementById(`fee-${rate.id}`)?.focus(); return; }
    const fee = parseCount(fees[rate.id]);
    setSaving(rate.id);
    try {
      await api('PUT', `/admin/shipping-rates/${rate.id}`, { fee });
      setRates((list) => list.map((r) => (r.id === rate.id ? { ...r, fee } : r)));
      setFees((f) => ({ ...f, [rate.id]: String(fee) }));
      toast(`Đã lưu phí ${rate.province}: ${formatVnd(fee)}`, { tone: 'success' });
    } catch (e) {
      toast(e.message, { tone: 'danger' });
    } finally {
      setSaving('');
    }
  }

  async function create(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const province = newRate.province.trim();
    const found = {};
    if (!province) found.province = 'Nhập tên tỉnh/thành';
    const feeMsg = countError(newRate.fee, 'Phí vận chuyển');
    if (feeMsg) found.fee = feeMsg;
    setNewErrors(found);
    if (Object.keys(found).length) {
      requestAnimationFrame(() => form.querySelector('[aria-invalid="true"]')?.focus());
      return;
    }
    setCreating(true);
    try {
      await api('POST', '/admin/shipping-rates', { province, fee: parseCount(newRate.fee) });
      setNewRate({ province: '', fee: DEFAULT_FEE });
      const list = await api('GET', '/admin/shipping-rates');
      setRates(list); // keep unsaved fee edits of the other rows
      setFees((prev) => Object.fromEntries(list.map((r) => [r.id, prev[r.id] ?? String(r.fee)])));
      toast(`Đã thêm ${province}`, { tone: 'success' });
    } catch (err) {
      toast(err.message, { tone: 'danger' });
    } finally {
      setCreating(false);
    }
  }

  let body;
  if (error && !rates) {
    body = (
      <EmptyState
        role="alert"
        tone="danger"
        icon={<AlertIcon size={26} />}
        title="Không tải được phí vận chuyển"
        action={<Button variant="dark" onClick={() => { setError(''); setAttempt((n) => n + 1); }}>Thử lại</Button>}
      >
        {error}
      </EmptyState>
    );
  } else if (!rates) {
    body = <div className="ad-card"><SkeletonRows label="Đang tải phí vận chuyển" count={8} /></div>;
  } else if (rates.length === 0) {
    body = <EmptyState icon={<TruckIcon size={26} />} title="Chưa có tỉnh/thành nào">Thêm tỉnh/thành đầu tiên ở biểu mẫu phía trên.</EmptyState>;
  } else if (visible.length === 0) {
    body = (
      <EmptyState
        icon={<SearchIcon size={26} />}
        title="Không tìm thấy tỉnh/thành"
        action={<Button variant="dark" onClick={() => setQuery('')}>Xoá tìm kiếm</Button>}
      >
        Kiểm tra lại từ khoá hoặc thêm tỉnh/thành mới.
      </EmptyState>
    );
  } else {
    body = (
      <div className="ad-card">
        <table className="ad-table ad-table--cards">
          <thead>
            <tr>
              <th scope="col">Tỉnh/Thành</th>
              <th scope="col">Phí (₫)</th>
              <th scope="col"><span className="sr-only">Lưu</span></th>
            </tr>
          </thead>
          <tbody>
            {visible.map((r) => {
              const draft = fees[r.id] ?? '';
              const dirty = draft !== String(r.fee);
              const message = rowErrors[r.id];
              return (
                <tr key={r.id} className="ad-srow">
                  <td className="ad-strong">{r.province}</td>
                  <td>
                    <div className="ad-feebox">
                      <div className="ad-feefield">
                        <label className="sr-only" htmlFor={`fee-${r.id}`}>Phí vận chuyển {r.province} (₫)</label>
                        <input
                          id={`fee-${r.id}`}
                          type="number"
                          min="0"
                          step="1"
                          inputMode="numeric"
                          value={draft}
                          aria-invalid={message ? true : undefined}
                          aria-describedby={message ? `fee-${r.id}-err` : undefined}
                          onChange={(e) => {
                            setFees({ ...fees, [r.id]: e.target.value });
                            if (message) setRowErrors((m) => ({ ...m, [r.id]: '' }));
                          }}
                          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); save(r); } }}
                        />
                        <span className="ad-feefield__unit" aria-hidden="true">₫</span>
                      </div>
                      {dirty && !message && <span className="ad-dirty">Chưa lưu</span>}
                    </div>
                    {message && <p className="ad-feeerr" id={`fee-${r.id}-err`} role="alert">{message}</p>}
                  </td>
                  <td className="is-right">
                    <Button
                      size="sm"
                      variant={dirty ? 'dark' : 'ghost'}
                      loading={saving === r.id}
                      disabled={saving !== '' && saving !== r.id}
                      aria-label={`Lưu phí ${r.province}`}
                      onClick={() => save(r)}
                    >
                      Lưu
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <>
      <AdminPageHeader title="Phí vận chuyển" description="Phí giao hàng theo tỉnh/thành, áp dụng khi khách thanh toán." />

      <form className="ad-card ad-add" onSubmit={create} noValidate aria-label="Thêm tỉnh/thành">
        <Field
          label="Tỉnh/Thành mới"
          value={newRate.province}
          error={newErrors.province}
          onChange={(e) => setNewRate({ ...newRate, province: e.target.value })}
          autoComplete="off"
        />
        <Field
          label="Phí (₫)"
          type="number"
          min="0"
          inputMode="numeric"
          value={newRate.fee}
          error={newErrors.fee}
          onChange={(e) => setNewRate({ ...newRate, fee: e.target.value })}
        />
        <Button type="submit" iconLeft={<PlusIcon size={18} />} loading={creating}>Thêm</Button>
      </form>

      <div className="ad-toolbar">
        <SearchBox value={query} onChange={setQuery} label="Tìm tỉnh/thành" placeholder="Tìm tỉnh/thành" />
        {rates && <span className="ad-toolbar__count" role="status">{query.trim() ? `${visible.length} / ${rates.length}` : rates.length} tỉnh/thành</span>}
      </div>

      {body}
    </>
  );
}
