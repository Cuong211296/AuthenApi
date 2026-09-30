import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import { formatVnd } from '../../utils/money.js';
import { NEXT_STATUSES, ORDER_STATUS_LABEL, PAYMENT_STATUS_LABEL } from '../../utils/labels.js';

const buildPath = (status, page) => {
  const query = new URLSearchParams({ page: String(page), size: '20' });
  if (status) query.set('status', status);
  return `/admin/orders?${query}`;
};

export default function AdminOrders() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const reload = () => api('GET', buildPath(status, page)).then(setData);

  useEffect(() => {
    let ignore = false;
    api('GET', buildPath(status, page))
      .then((d) => { if (!ignore) setData(d); })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [status, page]);

  async function change(code, next) {
    setError('');
    if (next === 'CANCELLED' && !window.confirm(`Huỷ đơn ${code}? Kho sẽ được hoàn lại.`)) return;
    try {
      await api('PUT', `/admin/orders/${code}/status`, { status: next });
      await reload();
    } catch (e) { setError(e.message); }
  }

  return (
    <div className="card">
      <h1>Đơn hàng</h1>
      {error && <p className="alert alert-error">{error}</p>}
      <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
        <option value="">Tất cả trạng thái</option>
        {Object.entries(ORDER_STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </select>
      {!data ? <p className="muted">Đang tải...</p> : (
        <table>
          <thead><tr><th>Mã</th><th>Khách</th><th>Trạng thái</th><th className="right">Tổng</th><th>Thao tác</th></tr></thead>
          <tbody>
            {data.items.map((o) => (
              <tr key={o.code}>
                <td>{o.code}<div className="muted">{new Date(o.createdAt).toLocaleString('vi-VN')}</div></td>
                <td>{o.receiverName}<div className="muted">{o.phone} · {o.province}</div></td>
                <td><span className="badge">{ORDER_STATUS_LABEL[o.status]}</span> <span className="badge">{PAYMENT_STATUS_LABEL[o.paymentStatus]}</span> <span className="badge">{o.paymentMethod}</span></td>
                <td className="right">{formatVnd(o.total)}</td>
                <td>{(NEXT_STATUSES[o.status] || []).map((n) => (
                  <button key={n} className={`btn ${n === 'CANCELLED' ? 'btn-danger' : ''}`} onClick={() => change(o.code, n)}>{ORDER_STATUS_LABEL[n]}</button>
                ))}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && data.totalPages > 1 && (
        <div className="pager">
          <button className="btn" disabled={page <= 0} onClick={() => setPage(page - 1)}>Trước</button>
          <span>Trang {page + 1} / {data.totalPages}</span>
          <button className="btn" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Sau</button>
        </div>
      )}
    </div>
  );
}
