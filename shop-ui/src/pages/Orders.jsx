import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { formatVnd } from '../utils/money.js';
import { ORDER_STATUS_LABEL, PAYMENT_STATUS_LABEL } from '../utils/labels.js';

export default function Orders() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let ignore = false;
    api('GET', `/orders?page=${page}&size=10`)
      .then((d) => { if (!ignore) setData(d); })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [page]);

  if (error) return <p className="alert alert-error">{error}</p>;
  if (!data) return <p className="muted">Đang tải...</p>;
  return (
    <div className="card">
      <h1>Đơn hàng của tôi</h1>
      {data.items.length === 0 && <p className="muted">Bạn chưa có đơn hàng nào.</p>}
      <table>
        <tbody>
          {data.items.map((o) => (
            <tr key={o.code}>
              <td><Link to={`/orders/${o.code}`}>{o.code}</Link><div className="muted">{new Date(o.createdAt).toLocaleString('vi-VN')}</div></td>
              <td><span className="badge">{ORDER_STATUS_LABEL[o.status]}</span> <span className="badge">{PAYMENT_STATUS_LABEL[o.paymentStatus]}</span></td>
              <td className="right">{formatVnd(o.total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.totalPages > 1 && (
        <div className="pager">
          <button className="btn" disabled={page <= 0} onClick={() => setPage(page - 1)}>Trước</button>
          <span>Trang {page + 1} / {data.totalPages}</span>
          <button className="btn" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Sau</button>
        </div>
      )}
    </div>
  );
}
