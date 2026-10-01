import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import OrderTimeline from '../components/orders/OrderTimeline.jsx';
import Badge from '../components/ui/Badge.jsx';
import Breadcrumbs from '../components/ui/Breadcrumbs.jsx';
import Button from '../components/ui/Button.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Skeleton } from '../components/ui/Skeleton.jsx';
import { AlertIcon, ArrowIcon, CheckIcon, ClockIcon, PinIcon } from '../components/ui/icons.jsx';
import { isOrderNotFound } from '../utils/orderErrors.js';
import { formatVnd } from '../utils/money.js';
import { shippingSourceLabel } from '../utils/shipping.js';
import {
  ORDER_STATUS_LABEL, ORDER_STATUS_TONE, PAYMENT_METHOD_LABEL, PAYMENT_STATUS_LABEL, PAYMENT_STATUS_TONE,
} from '../utils/labels.js';
import './OrderDetail.css';

function DetailSkeleton() {
  return (
    <div className="container od" role="status" aria-label="Đang tải đơn hàng">
      <div aria-hidden="true">
        <Skeleton variant="line" width={200} height={14} />
        <Skeleton variant="line" width="45%" height={40} style={{ marginTop: 20 }} />
        <div className="od__grid">
          <div className="od__main">
            <Skeleton height={120} radius={24} />
            <Skeleton height={260} radius={24} />
          </div>
          <div className="od__aside">
            <Skeleton height={200} radius={24} />
            <Skeleton height={160} radius={24} />
          </div>
        </div>
      </div>
    </div>
  );
}

export default function OrderDetail() {
  const { code } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  // The one-shot messages from checkout are read once, then removed from the history entry so a refresh does not replay them.
  const [flash] = useState(() => ({ placed: Boolean(location.state?.placed), payError: location.state?.payError || '' }));
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(flash.payError); // pay-start error, shown in the banner area once the order is loaded
  const [loadError, setLoadError] = useState(null); // failure of GET /orders/:code
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (location.state) navigate(`${location.pathname}${location.search}`, { replace: true, state: null });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let ignore = false;
    setLoadError(null);
    api('GET', `/orders/${code}`)
      .then((o) => { if (!ignore) setOrder(o); })
      .catch((e) => { if (!ignore) setLoadError(e); });
    return () => { ignore = true; };
  }, [code, attempt]);

  async function payWithMomo() {
    setBusy(true);
    setError('');
    try {
      const { payUrl } = await api('POST', `/orders/${code}/pay/momo`);
      if (typeof payUrl !== 'string' || !payUrl.startsWith('https://'))
        throw new Error('Không nhận được liên kết thanh toán hợp lệ');
      window.location.assign(payUrl);
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }

  if (!order) {
    if (!loadError) return <DetailSkeleton />;
    const notFound = isOrderNotFound(loadError);
    return (
      <div className="container od">
        <EmptyState
          tone="danger"
          role="alert"
          icon={<AlertIcon size={28} />}
          title={notFound ? 'Không tìm thấy đơn hàng' : 'Không tải được đơn hàng'}
          action={notFound
            ? <Button as={Link} to="/orders" variant="dark">Về danh sách đơn hàng</Button>
            : <Button variant="dark" onClick={() => setAttempt((n) => n + 1)}>Thử lại</Button>}
        >
          {loadError.message}
        </EmptyState>
      </div>
    );
  }

  const shipSource = shippingSourceLabel(order.shippingSource);
  const payable = order.status === 'PENDING_PAYMENT' && order.paymentMethod === 'MOMO'
    && order.expiresAt && new Date(order.expiresAt) > new Date();

  return (
    <div className="container od">
      <Breadcrumbs items={[{ label: 'Đơn hàng của tôi', to: '/orders' }, { label: order.code }]} />

      <header className="od__head">
        <div>
          <p className="eyebrow">Chi tiết đơn hàng</p>
          <h1 className="od__code tabular">{order.code}</h1>
          {order.createdAt && <p className="od__date">Đặt lúc {new Date(order.createdAt).toLocaleString('vi-VN')}</p>}
        </div>
        <p className="od__badges">
          <Badge tone={ORDER_STATUS_TONE[order.status] ?? 'neutral'} dot>{ORDER_STATUS_LABEL[order.status] ?? order.status}</Badge>
          <Badge tone={PAYMENT_STATUS_TONE[order.paymentStatus] ?? 'neutral'}>{PAYMENT_STATUS_LABEL[order.paymentStatus] ?? order.paymentStatus}</Badge>
        </p>
      </header>

      {flash.placed && (
        <p className="od-banner od-banner--ok" role="status">
          <CheckIcon size={20} strokeWidth={2.2} />
          <span>Đặt hàng thành công. Chúng tôi đã gửi email xác nhận.</span>
        </p>
      )}
      {error && (
        <p className="od-banner od-banner--error" role="alert">
          <AlertIcon size={20} />
          <span>{error}</span>
        </p>
      )}
      {payable && (
        <div className="od-banner od-banner--pay">
          <ClockIcon size={22} />
          <p>
            <strong>Đơn hàng đang chờ thanh toán</strong>
            <span>Hoàn tất thanh toán trước {new Date(order.expiresAt).toLocaleTimeString('vi-VN')} để giữ đơn hàng.</span>
          </p>
          <Button loading={busy} onClick={payWithMomo} iconRight={<ArrowIcon size={18} />}>Thanh toán với MoMo</Button>
        </div>
      )}

      <div className="od__grid">
        <div className="od__main">
          <section className="od-card" aria-labelledby="od-progress">
            <h2 id="od-progress" className="od-card__title">Tiến trình đơn hàng</h2>
            <OrderTimeline status={order.status} />
          </section>

          <section className="od-card" aria-labelledby="od-items">
            <h2 id="od-items" className="od-card__title">Sản phẩm ({order.items.length})</h2>
            <ul className="od-items">
              {order.items.map((i, idx) => (
                <li key={idx} className="od-item">
                  <span className="od-item__thumb" aria-hidden="true">{i.productName.slice(0, 1)}</span>
                  <div className="od-item__info">
                    <p className="od-item__name">{i.productName}</p>
                    <p className="od-item__variant">{i.size} · {i.color}</p>
                    <p className="od-item__qty tabular">{formatVnd(i.unitPrice)} × {i.quantity}</p>
                  </div>
                  <span className="od-item__total tabular">{formatVnd(i.lineTotal)}</span>
                </li>
              ))}
            </ul>
          </section>
        </div>

        <aside className="od__aside">
          <section className="od-card" aria-labelledby="od-total">
            <h2 id="od-total" className="od-card__title">Thanh toán</h2>
            <dl className="od-rows">
              <div><dt>Tạm tính</dt><dd className="tabular">{formatVnd(order.subtotal)}</dd></div>
              <div><dt>Phí vận chuyển</dt><dd className="od-fee">
                {shipSource && <Badge tone={shipSource.tone}>{shipSource.label}</Badge>}
                <span className="tabular">{formatVnd(order.shippingFee)}</span>
              </dd></div>
            </dl>
            <div className="od-total">
              <span>Tổng cộng</span>
              <strong className="tabular">{formatVnd(order.total)}</strong>
            </div>
            <dl className="od-rows od-rows--meta">
              <div><dt>Phương thức</dt><dd>{PAYMENT_METHOD_LABEL[order.paymentMethod] ?? order.paymentMethod}</dd></div>
              <div>
                <dt>Trạng thái</dt>
                <dd><Badge tone={PAYMENT_STATUS_TONE[order.paymentStatus] ?? 'neutral'} dot>{PAYMENT_STATUS_LABEL[order.paymentStatus] ?? order.paymentStatus}</Badge></dd>
              </div>
            </dl>
          </section>

          <section className="od-card" aria-labelledby="od-ship">
            <h2 id="od-ship" className="od-card__title"><PinIcon size={18} /> Giao đến</h2>
            <address className="od-address">
              <strong>{order.receiverName}</strong>
              <span>{order.phone}</span>
              <span>{[order.address, order.ward, order.province].filter(Boolean).join(', ')}</span>
            </address>
            {order.note && <p className="od-note">Ghi chú: {order.note}</p>}
          </section>
        </aside>
      </div>
    </div>
  );
}
