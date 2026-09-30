import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext.jsx';
import { useCartActions } from '../hooks/useCartActions.js';
import CartLines from '../components/cart/CartLines.jsx';
import Button from '../components/ui/Button.jsx';
import Badge from '../components/ui/Badge.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Skeleton } from '../components/ui/Skeleton.jsx';
import { ArrowIcon, BagIcon, RefreshIcon, ShieldIcon, TruckIcon } from '../components/ui/icons.jsx';
import { formatVnd } from '../utils/money.js';
import './Cart.css';

function CartSkeleton() {
  return (
    <div className="cart__grid" aria-hidden="true">
      <div className="cart__panel">
        {[0, 1, 2].map((i) => (
          <div key={i} className="cart__skel-row">
            <Skeleton width={96} height={128} radius={10} />
            <div className="cart__skel-text">
              <Skeleton variant="line" width="60%" />
              <Skeleton variant="line" width="30%" />
              <Skeleton variant="line" width="40%" height={36} />
            </div>
          </div>
        ))}
      </div>
      <Skeleton height={260} radius={24} />
    </div>
  );
}

export default function Cart() {
  const { cart, loaded, reload } = useCart();
  const { error, pendingId, changeQuantity, removeLine } = useCartActions();
  const navigate = useNavigate();

  useEffect(() => { reload(); }, [reload]);

  const hasUnavailable = cart.items.some((i) => !i.available);

  let content;
  if (!loaded) {
    content = <CartSkeleton />;
  } else if (cart.items.length === 0) {
    content = (
      <EmptyState
        icon={<BagIcon size={28} />}
        title="Giỏ hàng của bạn đang trống"
        action={<Button as={Link} to="/" variant="dark" iconRight={<ArrowIcon size={18} />}>Tiếp tục mua sắm</Button>}
      >
        Chưa có sản phẩm nào trong giỏ. Hãy khám phá bộ sưu tập mới và chọn món bạn thích.
      </EmptyState>
    );
  } else {
    content = (
      <>
        {error && <p className="cl-alert cl-alert--error cart__alert" role="alert">{error}</p>}
        {hasUnavailable && (
          <p className="cl-alert cl-alert--error cart__alert" role="alert">
            Một số sản phẩm đã hết hàng hoặc không đủ số lượng. Hãy chỉnh lại trước khi thanh toán.
          </p>
        )}
        <div className="cart__grid">
          <section className="cart__panel" aria-label="Sản phẩm trong giỏ">
            <CartLines items={cart.items} pendingId={pendingId} onChange={changeQuantity} onRemove={removeLine} />
          </section>

          <aside className="cart__summary" aria-labelledby="cart-summary-title">
            <h2 id="cart-summary-title" className="cart__summary-title">Tóm tắt đơn hàng</h2>
            <dl className="cart__rows">
              <div><dt>Tạm tính ({cart.totalQuantity} sản phẩm)</dt><dd className="tabular">{formatVnd(cart.subtotal)}</dd></div>
              <div><dt>Phí vận chuyển</dt><dd className="cart__muted">Tính ở bước thanh toán</dd></div>
            </dl>
            <div className="cart__total">
              <span>Tổng cộng</span>
              <strong className="tabular">{formatVnd(cart.subtotal)}</strong>
            </div>
            {hasUnavailable && <Badge tone="danger" dot>Cần chỉnh lại giỏ hàng</Badge>}
            <Button
              block
              size="lg"
              disabled={hasUnavailable}
              aria-describedby={hasUnavailable ? 'cart-block-note' : undefined}
              iconRight={<ArrowIcon size={18} />}
              onClick={() => navigate('/checkout')}
            >
              Thanh toán
            </Button>
            {hasUnavailable && <p id="cart-block-note" className="cart__note">Bạn cần xoá hoặc giảm số lượng các sản phẩm không đủ hàng để tiếp tục.</p>}
            <Link to="/" className="cart__continue">Tiếp tục mua sắm</Link>
            <ul className="cart__perks">
              <li><TruckIcon size={18} /> Giao nhanh toàn quốc</li>
              <li><RefreshIcon size={18} /> Đổi trả trong 7 ngày</li>
              <li><ShieldIcon size={18} /> Thanh toán an toàn</li>
            </ul>
          </aside>
        </div>
      </>
    );
  }

  return (
    <div className="container cart">
      <header className="cart__head">
        <p className="eyebrow">Xem lại đơn hàng</p>
        <h1>Giỏ hàng</h1>
      </header>
      {content}
    </div>
  );
}
