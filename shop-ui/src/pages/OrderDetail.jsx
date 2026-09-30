import { useEffect, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { formatVnd } from '../utils/money.js';
import { ORDER_STATUS_LABEL, PAYMENT_STATUS_LABEL } from '../utils/labels.js';

export default function OrderDetail() {
  const { code } = useParams();
  const location = useLocation();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(location.state?.payError || '');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let ignore = false;
    api('GET', `/orders/${code}`)
      .then((o) => { if (!ignore) setOrder(o); })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [code]);

  async function payWithMomo() {
    setBusy(true);
    setError('');
    try {
      const { payUrl } = await api('POST', `/orders/${code}/pay/momo`);
      window.location.assign(payUrl);
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }

  if (!order) return error ? <p className="alert alert-error">{error}</p> : <p className="muted">Đang tải...</p>;
  const payable = order.status === 'PENDING_PAYMENT' && order.paymentMethod === 'MOMO'
    && order.expiresAt && new Date(order.expiresAt) > new Date();

  return (
    <div className="card">
      <h1>Đơn hàng {order.code}</h1>
      {location.state?.placed && <p className="alert alert-ok">Đặt hàng thành công. Chúng tôi đã gửi email xác nhận.</p>}
      {error && <p className="alert alert-error">{error}</p>}
      <p><span className="badge">{ORDER_STATUS_LABEL[order.status]}</span> <span className="badge">{PAYMENT_STATUS_LABEL[order.paymentStatus]}</span> <span className="badge">{order.paymentMethod}</span></p>
      {payable && (
        <p className="alert alert-info">
          Đơn hàng chờ thanh toán đến {new Date(order.expiresAt).toLocaleTimeString('vi-VN')}.{' '}
          <button className="btn btn-primary" disabled={busy} onClick={payWithMomo}>Thanh toán với MoMo</button>
        </p>
      )}
      <table>
        <thead><tr><th>Sản phẩm</th><th>SL</th><th className="right">Thành tiền</th></tr></thead>
        <tbody>
          {order.items.map((i, idx) => (
            <tr key={idx}><td>{i.productName}<div className="muted">{i.size} / {i.color}</div></td><td>{i.quantity}</td><td className="right">{formatVnd(i.lineTotal)}</td></tr>
          ))}
        </tbody>
      </table>
      <p className="right">Tạm tính: {formatVnd(order.subtotal)}<br />Phí vận chuyển: {formatVnd(order.shippingFee)}<br /><strong>Tổng cộng: {formatVnd(order.total)}</strong></p>
      <p>Giao đến: {order.receiverName}, {order.phone}<br />{order.address}, {order.province}</p>
    </div>
  );
}
