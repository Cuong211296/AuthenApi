import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';
import { useCartDrawer } from '../context/CartDrawerContext.jsx';
import { useAnimatedNumber } from '../hooks/useAnimatedNumber.js';
import Breadcrumbs from '../components/ui/Breadcrumbs.jsx';
import Button from '../components/ui/Button.jsx';
import Badge from '../components/ui/Badge.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import Stepper from '../components/ui/Stepper.jsx';
import Swatch from '../components/ui/Swatch.jsx';
import ToggleGroup from '../components/ui/ToggleGroup.jsx';
import { Skeleton } from '../components/ui/Skeleton.jsx';
import { useToast } from '../components/ui/Toast.jsx';
import { BagIcon, RefreshIcon, SearchIcon, ShieldIcon, TruckIcon } from '../components/ui/icons.jsx';
import ProductGallery from './product/ProductGallery.jsx';
import { formatVnd } from '../utils/money.js';
import { clampQuantity, maxQuantityFor, stockLevel } from '../utils/quantity.js';
import { colorsFor, findVariant, sizesOf } from '../utils/variants.js';
import './ProductDetail.css';

/** Mounted only after the product is loaded, so the first render starts at the real price (no count-up from 0). */
function AnimatedPrice({ price }) {
  const shown = useAnimatedNumber(price);
  return <p className="pd__price tabular">{formatVnd(shown)}</p>;
}

function ProductSkeleton() {
  return (
    <div className="container pd" role="status" aria-label="Đang tải sản phẩm">
      <Skeleton variant="line" width={220} height={14} />
      <div className="pd__grid" aria-hidden="true">
        <Skeleton className="pd__skel-media" radius={24} />
        <div className="pd__skel-panel">
          <Skeleton variant="line" width="30%" height={12} />
          <Skeleton variant="line" width="85%" height={38} />
          <Skeleton variant="line" width="35%" height={28} />
          <Skeleton variant="line" width="100%" />
          <Skeleton variant="line" width="80%" />
          <Skeleton variant="line" width="25%" height={12} />
          <div className="pd__skel-chips">{[0, 1, 2, 3].map((i) => <Skeleton key={i} width={56} height={44} radius={999} />)}</div>
          <Skeleton height={54} radius={999} />
        </div>
      </div>
    </div>
  );
}

const BENEFITS = [
  { icon: <TruckIcon size={20} />, title: 'Giao nhanh toàn quốc', text: 'Nhận hàng trong 2 đến 4 ngày' },
  { icon: <RefreshIcon size={20} />, title: 'Đổi trả dễ dàng', text: 'Trong vòng 7 ngày' },
  { icon: <ShieldIcon size={20} />, title: 'Thanh toán an toàn', text: 'MoMo hoặc COD' },
];

export default function ProductDetail() {
  const { slug } = useParams();
  const { session } = useAuth();
  const { add } = useCart();
  const { openCart } = useCartDrawer();
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [product, setProduct] = useState(null);
  const [error, setError] = useState('');
  const [size, setSize] = useState('');
  const [color, setColor] = useState('');
  const [qty, setQty] = useState(1);
  const [addError, setAddError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let ignore = false;
    setProduct(null);
    setError('');
    setSize('');
    setColor('');
    setQty(1);
    setAddError('');
    api('GET', `/products/${slug}`, undefined, { auth: false })
      .then((r) => { if (!ignore) setProduct(r); })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [slug]);

  const variants = product?.variants ?? [];
  const sizes = useMemo(() => sizesOf(variants), [variants]);
  const sizeOptions = useMemo(
    () => sizes.map((s) => ({ value: s, disabled: !variants.some((v) => v.size === s && v.stock > 0) })),
    [sizes, variants],
  );
  const colors = useMemo(() => (size ? colorsFor(variants, size) : []), [variants, size]);
  const variant = size && color ? findVariant(variants, size, color) : undefined;

  const stock = variant?.stock ?? 0;
  const level = variant ? stockLevel(stock) : null;
  const maxQty = variant ? maxQuantityFor(stock) : 99;
  const quantity = clampQuantity(qty, 1, Math.max(1, maxQty));
  // Keep the stored quantity valid when the variant (and so the stock) changes.
  useEffect(() => { setQty((q) => clampQuantity(q, 1, Math.max(1, maxQty))); }, [maxQty]);

  const price = variant ? variant.price : product?.basePrice ?? 0;

  async function addToCart() {
    setAddError('');
    if (!session) return navigate('/login', { state: { from: location } });
    setBusy(true);
    try {
      await add(variant.id, quantity);
      toast('Đã thêm vào giỏ hàng', { tone: 'success', action: { label: 'Xem giỏ hàng', onClick: openCart } });
    } catch (e) {
      setAddError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (error && !product) {
    return (
      <div className="container pd pd--state">
        <EmptyState
          tone="danger"
          role="alert"
          icon={<SearchIcon size={26} />}
          title="Không tìm thấy sản phẩm"
          action={<Button as={Link} to="/" variant="dark">Về cửa hàng</Button>}
        >
          {error}
        </EmptyState>
      </div>
    );
  }
  if (!product) return <ProductSkeleton />;

  const crumbs = [
    { label: 'Cửa hàng', to: '/' },
    ...(product.category ? [{ label: product.category.name, to: `/?category=${product.category.slug}` }] : []),
    { label: product.name },
  ];
  const soldOut = variant && level === 'out';
  const canAdd = Boolean(variant) && !soldOut;

  return (
    <div className="container pd">
      <Breadcrumbs items={crumbs} />
      <div className="pd__grid">
        <div className="pd__media">
          <ProductGallery src={product.imageUrl} name={product.name} />
        </div>

        <div className="pd__panel">
          {product.category && <p className="eyebrow">{product.category.name}</p>}
          <h1 className="pd__title">{product.name}</h1>
          <AnimatedPrice price={price} />
          {product.description && <p className="pd__desc">{product.description}</p>}

          <div className="pd__field">
            <div className="pd__label"><span>Size</span></div>
            <ToggleGroup
              label="Chọn size"
              options={sizeOptions}
              value={size}
              onChange={(s) => { setSize(s); setColor(''); }}
            />
          </div>

          <div className="pd__field">
            <div className="pd__label">
              <span>Màu</span>
              {color && <span className="pd__label-value">{color}</span>}
            </div>
            {size ? (
              <div className="pd__swatches" role="group" aria-label="Chọn màu">
                {colors.map((c) => (
                  <Swatch key={c.color} color={c.color} available={c.available} selected={color === c.color} onClick={() => setColor(c.color)} />
                ))}
              </div>
            ) : (
              <p className="pd__hint">Chọn size trước để xem các màu.</p>
            )}
          </div>

          <div className="pd__stock" aria-live="polite">
            {level === 'out' && <Badge tone="danger" dot>Hết hàng</Badge>}
            {level === 'low' && <Badge tone="warn" dot>Còn {stock} sản phẩm, sắp hết</Badge>}
            {level === 'ok' && <Badge tone="success" dot>Còn {stock} sản phẩm</Badge>}
          </div>

          <div className="pd__buy">
            <Stepper
              value={quantity}
              onChange={setQty}
              min={1}
              max={Math.max(1, maxQty)}
              disabled={!canAdd}
              label="Số lượng"
            />
            <Button
              size="lg"
              className="pd__add"
              loading={busy}
              disabled={!canAdd}
              iconLeft={<BagIcon size={20} />}
              onClick={addToCart}
            >
              {soldOut ? 'Hết hàng' : 'Thêm vào giỏ'}
            </Button>
          </div>
          {!variant && <p className="pd__hint pd__hint--below">Vui lòng chọn size và màu để thêm vào giỏ.</p>}
          {addError && <p className="cl-alert cl-alert--error" role="alert">{addError}</p>}

          <ul className="pd__benefits">
            {BENEFITS.map((b) => (
              <li key={b.title}>
                <span className="pd__benefit-icon">{b.icon}</span>
                <span><strong>{b.title}</strong><small>{b.text}</small></span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
