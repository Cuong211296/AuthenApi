import { useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import Drawer from '../ui/Drawer.jsx';
import Button from '../ui/Button.jsx';
import EmptyState from '../ui/EmptyState.jsx';
import { Skeleton } from '../ui/Skeleton.jsx';
import { ArrowIcon, BagIcon, UserIcon } from '../ui/icons.jsx';
import CartLines from './CartLines.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { useCart } from '../../context/CartContext.jsx';
import { useToast } from '../ui/Toast.jsx';
import { useCartDrawer } from '../../context/CartDrawerContext.jsx';
import { useCartActions } from '../../hooks/useCartActions.js';
import { formatVnd } from '../../utils/money.js';
import './CartDrawer.css';

function LoadingLines() {
  return (
    <div className="cd__loading" aria-hidden="true">
      {[0, 1].map((i) => (
        <div key={i} className="cd__loading-row">
          <Skeleton width={76} height={101} radius={10} />
          <div className="cd__loading-text">
            <Skeleton variant="line" width="80%" />
            <Skeleton variant="line" width="40%" />
            <Skeleton variant="line" width="55%" height={30} />
          </div>
        </div>
      ))}
    </div>
  );
}

/** Right-side mini cart. Open state comes from CartDrawerContext (header button, product toast action). */
export default function CartDrawer() {
  const { open, closeCart } = useCartDrawer();
  const { session } = useAuth();
  const { cart, loaded, reload } = useCart();
  const { dismissAll } = useToast();
  const { error, clearError, pendingId, changeQuantity, removeLine } = useCartActions();
  const navigate = useNavigate();
  const location = useLocation();

  // Always show fresh stock/prices when the drawer opens.
  useEffect(() => {
    if (open) dismissAll(); // the drawer already shows the cart, so an "added" toast would only cover its header
    if (open && session) { clearError(); reload(); }
    // clearError is recreated each render; only the open transition matters here.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, session, reload, dismissAll]);

  const items = cart.items;
  const hasUnavailable = items.some((i) => !i.available);
  const showLines = session && items.length > 0;

  let footer = null;
  if (showLines) {
    footer = (
      <div className="cd__foot">
        <div className="cd__subtotal">
          <span>Tạm tính</span>
          <strong className="tabular">{formatVnd(cart.subtotal)}</strong>
        </div>
        <p className="cd__note" id="cd-note">
          {hasUnavailable
            ? 'Một số sản phẩm đã hết hàng hoặc không đủ số lượng. Hãy chỉnh lại trước khi thanh toán.'
            : 'Phí vận chuyển được tính ở bước thanh toán.'}
        </p>
        <div className="cd__actions">
          <Button
            block
            size="lg"
            disabled={hasUnavailable}
            aria-describedby="cd-note"
            iconRight={<ArrowIcon size={18} />}
            onClick={() => navigate('/checkout')}
          >
            Thanh toán
          </Button>
          <Button as={Link} to="/cart" variant="ghost" block>Xem giỏ hàng</Button>
        </div>
      </div>
    );
  }

  let body;
  if (!session) {
    body = (
      <EmptyState
        className="cd__empty"
        icon={<UserIcon size={26} />}
        title="Đăng nhập để xem giỏ hàng"
        action={<Button as={Link} to="/login" state={{ from: location }}>Đăng nhập</Button>}
      >
        Giỏ hàng gắn với tài khoản của bạn, nên bạn cần đăng nhập để thêm và xem sản phẩm.
      </EmptyState>
    );
  } else if (!loaded) {
    body = <LoadingLines />;
  } else if (items.length === 0) {
    body = (
      <EmptyState
        className="cd__empty"
        icon={<BagIcon size={26} />}
        title="Giỏ hàng đang trống"
        action={<Button as={Link} to="/" variant="dark" iconRight={<ArrowIcon size={18} />}>Tiếp tục mua sắm</Button>}
      >
        Hãy chọn vài món bạn thích, chúng sẽ xuất hiện ở đây.
      </EmptyState>
    );
  } else {
    body = (
      <>
        {error && <p className="cl-alert cl-alert--error cd__error" role="alert">{error}</p>}
        <CartLines items={items} compact pendingId={pendingId} onChange={changeQuantity} onRemove={removeLine} />
      </>
    );
  }

  const title = showLines ? `Giỏ hàng (${cart.totalQuantity})` : 'Giỏ hàng';
  return (
    <Drawer open={open} onClose={closeCart} title={title} footer={footer} className="cd" width={460}>
      {body}
    </Drawer>
  );
}
