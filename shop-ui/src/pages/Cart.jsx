import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext.jsx';
import { formatVnd } from '../utils/money.js';

export default function Cart() {
  const { cart, update, remove } = useCart();
  const navigate = useNavigate();
  const [error, setError] = useState('');

  const run = async (fn) => {
    setError('');
    try { await fn(); } catch (e) { setError(e.message); }
  };
  const hasUnavailable = cart.items.some((i) => !i.available);

  if (cart.items.length === 0) {
    return <div className="card"><p>Giỏ hàng trống.</p><Link to="/">Tiếp tục mua sắm</Link></div>;
  }
  return (
    <div className="card">
      <h1>Giỏ hàng</h1>
      {error && <p className="alert alert-error">{error}</p>}
      {hasUnavailable && <p className="alert alert-error">Một số sản phẩm đã hết hàng hoặc không đủ số lượng. Hãy chỉnh lại trước khi thanh toán.</p>}
      <table>
        <thead><tr><th>Sản phẩm</th><th>Đơn giá</th><th>SL</th><th className="right">Thành tiền</th><th /></tr></thead>
        <tbody>
          {cart.items.map((i) => (
            <tr key={i.variantId}>
              <td><Link to={`/products/${i.productSlug}`}>{i.productName}</Link><div className="muted">{i.size} / {i.color}{!i.available && ' · không đủ hàng'}</div></td>
              <td>{formatVnd(i.unitPrice)}</td>
              <td>
                <input type="number" min="1" max="99" defaultValue={i.quantity} style={{ width: 70 }}
                  onBlur={(e) => { const q = Number(e.target.value); if (q >= 1 && q !== i.quantity) run(() => update(i.variantId, q)); }} />
              </td>
              <td className="right">{formatVnd(i.lineTotal)}</td>
              <td><button className="btn btn-danger" onClick={() => run(() => remove(i.variantId))}>Xoá</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="right"><strong>Tạm tính: {formatVnd(cart.subtotal)}</strong></p>
      <p className="right"><button className="btn btn-primary" disabled={hasUnavailable} onClick={() => navigate('/checkout')}>Thanh toán</button></p>
    </div>
  );
}
