import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';
import { formatVnd } from '../utils/money.js';
import { colorsFor, findVariant, sizesOf } from '../utils/variants.js';

export default function ProductDetail() {
  const { slug } = useParams();
  const { session } = useAuth();
  const { add } = useCart();
  const navigate = useNavigate();
  const location = useLocation();

  const [product, setProduct] = useState(null);
  const [error, setError] = useState('');
  const [size, setSize] = useState('');
  const [color, setColor] = useState('');
  const [qty, setQty] = useState(1);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let ignore = false;
    setProduct(null);
    setError('');
    api('GET', `/products/${slug}`, undefined, { auth: false })
      .then((r) => { if (!ignore) setProduct(r); })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [slug]);

  const variants = product?.variants ?? [];
  const sizes = useMemo(() => sizesOf(variants), [variants]);
  const colors = useMemo(() => (size ? colorsFor(variants, size) : []), [variants, size]);
  const variant = size && color ? findVariant(variants, size, color) : undefined;

  async function addToCart() {
    setMessage('');
    if (!session) return navigate('/login', { state: { from: location } });
    setBusy(true);
    try {
      await add(variant.id, qty);
      setMessage('Đã thêm vào giỏ hàng');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (error && !product) return <p className="alert alert-error">{error}</p>;
  if (!product) return <p className="muted">Đang tải...</p>;

  const price = variant ? variant.price : product.basePrice;
  return (
    <div className="card row">
      <div>
        {product.imageUrl ? <img src={product.imageUrl} alt={product.name} style={{ width: '100%', borderRadius: 8 }} /> : <div className="img-placeholder">Chưa có ảnh</div>}
      </div>
      <div>
        <h1>{product.name}</h1>
        <p className="price" style={{ fontSize: '1.4rem' }}>{formatVnd(price)}</p>
        {product.description && <p>{product.description}</p>}

        <strong>Size</strong>
        <div className="chips">
          {sizes.map((s) => (
            <button key={s} aria-pressed={size === s} className={`chip ${size === s ? 'on' : ''}`} onClick={() => { setSize(s); setColor(''); }}>{s}</button>
          ))}
        </div>

        <strong>Màu</strong>
        <div className="chips">
          {!size && <span className="muted">Chọn size trước</span>}
          {colors.map((c) => (
            <button key={c.color} aria-pressed={color === c.color} disabled={!c.available} className={`chip ${color === c.color ? 'on' : ''}`} onClick={() => setColor(c.color)}>{c.color}</button>
          ))}
        </div>

        {variant && <p className="muted">Còn {variant.stock} sản phẩm</p>}
        <label className="form" style={{ maxWidth: 120 }}>Số lượng
          <input type="number" min="1" max={variant ? Math.min(variant.stock, 99) : 99} value={qty}
            onChange={(e) => setQty(Math.max(1, Number(e.target.value) || 1))} />
        </label>

        {error && <p className="alert alert-error">{error}</p>}
        {message && <p className="alert alert-ok">{message}</p>}
        <p><button className="btn btn-primary" disabled={!variant || busy} onClick={addToCart}>Thêm vào giỏ</button></p>
      </div>
    </div>
  );
}
