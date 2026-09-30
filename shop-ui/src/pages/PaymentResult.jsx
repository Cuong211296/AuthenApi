import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';

export default function PaymentResult() {
  const [params] = useSearchParams();
  const orderCode = params.get('orderCode');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const check = useCallback(async (isIgnored = () => false) => {
    if (!orderCode) return setError('Thiếu mã đơn hàng.');
    setBusy(true);
    setError('');
    try {
      const r = await api('GET', `/payments/momo/return?orderCode=${encodeURIComponent(orderCode)}`);
      if (!isIgnored()) setResult(r);
    } catch (e) {
      if (!isIgnored()) setError(e.message);
    } finally {
      if (!isIgnored()) setBusy(false);
    }
  }, [orderCode]);

  useEffect(() => {
    let ignore = false;
    check(() => ignore);
    return () => { ignore = true; };
  }, [check]);

  return (
    <div className="card narrow">
      <h1>Kết quả thanh toán</h1>
      {busy && !result && <p className="muted">Đang xác nhận với MoMo...</p>}
      {error && <p className="alert alert-error">{error}</p>}
      {result?.paymentStatus === 'PAID' && <p className="alert alert-ok">Thanh toán thành công. Cảm ơn bạn! Email xác nhận sẽ được gửi tới bạn.</p>}
      {result && result.paymentStatus !== 'PAID' && result.orderStatus === 'PENDING_PAYMENT' && (
        <p className="alert alert-info">Chưa nhận được xác nhận thanh toán. Nếu bạn đã thanh toán, hãy bấm "Kiểm tra lại" sau vài giây.</p>
      )}
      {result && result.orderStatus === 'CANCELLED' && <p className="alert alert-error">Đơn hàng đã bị huỷ (hết hạn thanh toán).</p>}
      <p>
        <button className="btn" onClick={() => check()} disabled={busy}>Kiểm tra lại</button>{' '}
        {orderCode && <Link to={`/orders/${orderCode}`}>Xem đơn hàng</Link>}
      </p>
    </div>
  );
}
