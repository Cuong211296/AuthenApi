import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client.js';
import { useCart } from '../context/CartContext.jsx';
import { formatVnd } from '../utils/money.js';
import { isValidPhone, normalizeCheckoutForm } from '../utils/checkout.js';

export default function Checkout() {
  const { cart, reload } = useCart();
  const navigate = useNavigate();
  const [provinces, setProvinces] = useState([]);
  const [form, setForm] = useState({ receiverName: '', phone: '', email: '', address: '', province: '', note: '', paymentMethod: 'MOMO' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const [loaded, setLoaded] = useState(false);
  const [placed, setPlaced] = useState(false);

  useEffect(() => {
    let ignore = false;
    api('GET', '/shipping/provinces', undefined, { auth: false })
      .then((list) => { if (!ignore) setProvinces(list); })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, []);

  // Refresh availability/stock before the customer submits.
  useEffect(() => {
    let ignore = false;
    reload().finally(() => { if (!ignore) setLoaded(true); });
    return () => { ignore = true; };
  }, [reload]);

  const shippingFee = useMemo(() => provinces.find((p) => p.province === form.province)?.fee ?? 0, [provinces, form.province]);
  const hasUnavailable = cart.items.some((i) => !i.available);

  async function submit(e) {
    e.preventDefault();
    setError('');
    const payload = normalizeCheckoutForm(form);
    if (!isValidPhone(payload.phone)) return setError('Số điện thoại không hợp lệ (ví dụ 0901234567)');
    if (!payload.province) return setError('Hãy chọn tỉnh/thành');
    setBusy(true);
    let order;
    try {
      order = await api('POST', '/orders', payload);
    } catch (err) {
      setBusy(false);
      return setError(err.code === 1011
        ? 'Thông tin chưa hợp lệ. Hãy kiểm tra lại họ tên, số điện thoại, email và địa chỉ.'
        : err.message);
    }
    setPlaced(true);
    await reload(); // the server emptied the cart
    if (form.paymentMethod === 'COD') return navigate(`/orders/${order.code}`, { replace: true, state: { placed: true } });
    try {
      const { payUrl } = await api('POST', `/orders/${order.code}/pay/momo`);
      if (typeof payUrl !== 'string' || !payUrl.startsWith('https://'))
        throw new Error('Không nhận được liên kết thanh toán hợp lệ');
      window.location.assign(payUrl);
    } catch (err) {
      // The order exists; let the customer retry from its page.
      navigate(`/orders/${order.code}`, { replace: true, state: { payError: err.message } });
    }
  }

  if (placed) return <p className="muted">Đang xử lý đơn hàng...</p>;
  if (!loaded) return <p className="muted">Đang tải...</p>;
  if (cart.items.length === 0) return <div className="card"><p>Giỏ hàng trống.</p><Link to="/">Tiếp tục mua sắm</Link></div>;

  return (
    <form className="row" onSubmit={submit}>
      <div className="card form">
        <h1>Thông tin giao hàng</h1>
        {error && <p className="alert alert-error">{error}</p>}
        {hasUnavailable && <p className="alert alert-error">Giỏ hàng có sản phẩm không đủ hàng. <Link to="/cart">Chỉnh giỏ hàng</Link></p>}
        <label>Họ tên người nhận<input value={form.receiverName} onChange={set('receiverName')} required maxLength={100} /></label>
        <label>Số điện thoại<input value={form.phone} onChange={set('phone')} required inputMode="tel" /></label>
        <label>Email nhận xác nhận đơn<input type="email" value={form.email} onChange={set('email')} required /></label>
        <label>Tỉnh/Thành
          <select value={form.province} onChange={set('province')} required>
            <option value="">-- Chọn --</option>
            {provinces.map((p) => <option key={p.id} value={p.province}>{p.province}</option>)}
          </select>
        </label>
        <label>Địa chỉ<input value={form.address} onChange={set('address')} required maxLength={300} /></label>
        <label>Ghi chú<textarea value={form.note} onChange={set('note')} maxLength={500} rows={2} /></label>
      </div>

      <div className="card form">
        <h2>Đơn hàng</h2>
        {cart.items.map((i) => (
          <div key={i.variantId} className="row" style={{ gap: 8 }}>
            <span>{i.productName} ({i.size}/{i.color}) × {i.quantity}</span>
            <span className="right">{formatVnd(i.lineTotal)}</span>
          </div>
        ))}
        <hr />
        <div className="row"><span>Tạm tính</span><span className="right">{formatVnd(cart.subtotal)}</span></div>
        <div className="row"><span>Phí vận chuyển</span><span className="right">{form.province ? formatVnd(shippingFee) : '—'}</span></div>
        <div className="row"><strong>Tổng cộng</strong><strong className="right price">{formatVnd(cart.subtotal + shippingFee)}</strong></div>

        <h3>Phương thức thanh toán</h3>
        <label><input type="radio" name="pm" checked={form.paymentMethod === 'MOMO'} onChange={() => setForm({ ...form, paymentMethod: 'MOMO' })} /> MoMo (ví, thẻ ATM, thẻ Visa/Mastercard)</label>
        <label><input type="radio" name="pm" checked={form.paymentMethod === 'COD'} onChange={() => setForm({ ...form, paymentMethod: 'COD' })} /> Thanh toán khi nhận hàng (COD)</label>
        <button className="btn btn-primary" disabled={busy || hasUnavailable}>{busy ? 'Đang xử lý...' : form.paymentMethod === 'MOMO' ? 'Thanh toán với MoMo' : 'Đặt hàng'}</button>
        <p className="muted">Giá cuối cùng được máy chủ tính lại khi đặt hàng.</p>
      </div>
    </form>
  );
}
