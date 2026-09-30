import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client.js';
import { useCart } from '../context/CartContext.jsx';
import { useAnimatedNumber } from '../hooks/useAnimatedNumber.js';
import { formatVnd } from '../utils/money.js';
import { checkoutErrors, normalizeCheckoutForm } from '../utils/checkout.js';
import Button from '../components/ui/Button.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import Field from '../components/ui/Field.jsx';
import ProgressSteps from '../components/ui/ProgressSteps.jsx';
import RadioCards from '../components/ui/RadioCards.jsx';
import { Skeleton } from '../components/ui/Skeleton.jsx';
import { AlertIcon, ArrowIcon, BagIcon, CashIcon, LockIcon, ShieldIcon } from '../components/ui/icons.jsx';
import './Checkout.css';

const PAYMENT_OPTIONS = [
  {
    value: 'MOMO',
    title: 'Ví MoMo',
    description: 'Thanh toán qua ví MoMo, thẻ ATM hoặc thẻ Visa/Mastercard.',
    icon: <span className="co-momo" aria-hidden="true">mo</span>,
  },
  {
    value: 'COD',
    title: 'Thanh toán khi nhận hàng',
    description: 'Trả tiền mặt cho người giao hàng khi nhận đơn (COD).',
    icon: <CashIcon size={22} />,
  },
];

const FIELD_ORDER = ['receiverName', 'phone', 'email', 'province', 'address'];

/** Counts smoothly to the new amount (mounted once, so the first render shows the real value). */
function Money({ value, className }) {
  const shown = useAnimatedNumber(value);
  return <span className={`tabular ${className ?? ''}`}>{formatVnd(shown)}</span>;
}

function Thumb({ item }) {
  const [failed, setFailed] = useState(false);
  return (
    <span className="co-thumb" aria-hidden="true">
      {item.imageUrl && !failed
        ? <img src={item.imageUrl} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />
        : <span className="co-thumb__fallback">{item.productName.slice(0, 1)}</span>}
      <span className="co-thumb__qty tabular">{item.quantity}</span>
    </span>
  );
}

function CheckoutSkeleton() {
  return (
    <div className="co__grid" role="status" aria-label="Đang tải">
      <div className="co__main" aria-hidden="true">
        <Skeleton height={430} radius={24} />
        <Skeleton height={190} radius={24} />
      </div>
      <Skeleton height={380} radius={24} />
    </div>
  );
}

export default function Checkout() {
  const { cart, reload } = useCart();
  const navigate = useNavigate();
  const [provinces, setProvinces] = useState([]);
  const [form, setForm] = useState({ receiverName: '', phone: '', email: '', address: '', province: '', note: '', paymentMethod: 'MOMO' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false); // guards double submits that arrive before `busy` re-renders
  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const set = (key) => (e) => { const { value } = e.target; setForm((f) => ({ ...f, [key]: value })); };
  const blur = (key) => () => setTouched((t) => ({ ...t, [key]: true }));

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
  const errors = useMemo(() => checkoutErrors(form), [form]);
  const shown = (key) => (submitted || touched[key] ? errors[key] : undefined);

  async function submit(e) {
    e.preventDefault();
    if (busy || inFlight.current) return;
    setError('');
    setSubmitted(true);
    const firstInvalid = FIELD_ORDER.find((key) => errors[key]);
    if (firstInvalid) {
      document.getElementById(`co-${firstInvalid}`)?.focus();
      return;
    }
    const payload = normalizeCheckoutForm(form);
    inFlight.current = true;
    setBusy(true);
    let order;
    try {
      order = await api('POST', '/orders', payload);
    } catch (err) {
      inFlight.current = false;
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

  const stage = placed ? 3 : busy ? 2 : 1;
  const steps = ['Thông tin', 'Thanh toán', 'Hoàn tất'].map((label, i) => ({
    key: label,
    label,
    state: i + 1 < stage ? 'done' : i + 1 === stage ? 'current' : 'todo',
  }));
  const isMomo = form.paymentMethod === 'MOMO';

  let content;
  if (placed) {
    content = (
      <div className="co__processing" role="status">
        <span className="spinner co__processing-spinner" aria-hidden="true" />
        <h2>{isMomo ? 'Đang chuyển tới MoMo...' : 'Đang hoàn tất đơn hàng...'}</h2>
        <p>Đang xử lý đơn hàng. Vui lòng không đóng trang này.</p>
      </div>
    );
  } else if (!loaded) {
    content = <CheckoutSkeleton />;
  } else if (cart.items.length === 0) {
    content = (
      <EmptyState
        icon={<BagIcon size={28} />}
        title="Giỏ hàng trống"
        action={<Button as={Link} to="/" variant="dark" iconRight={<ArrowIcon size={18} />}>Tiếp tục mua sắm</Button>}
      >
        Hãy thêm sản phẩm vào giỏ trước khi thanh toán.
      </EmptyState>
    );
  } else {
    content = (
      <form className="co__grid" onSubmit={submit} noValidate>
        <div className="co__main">
          {error && <p className="co-alert co-alert--error" role="alert"><AlertIcon size={18} /><span>{error}</span></p>}
          {hasUnavailable && (
            <p className="co-alert co-alert--error" role="alert">
              <AlertIcon size={18} />
              <span>Giỏ hàng có sản phẩm không đủ hàng. <Link to="/cart">Chỉnh giỏ hàng</Link></span>
            </p>
          )}

          <section className="co-card" aria-labelledby="co-ship-title">
            <h2 id="co-ship-title" className="co-card__title"><span className="co-card__num">1</span>Thông tin giao hàng</h2>
            <div className="co-fields">
              <Field
                id="co-receiverName" label="Họ tên người nhận" value={form.receiverName} onChange={set('receiverName')}
                onBlur={blur('receiverName')} error={shown('receiverName')} maxLength={100} autoComplete="name" required
                placeholder="Nguyễn Văn A"
              />
              <Field
                id="co-phone" label="Số điện thoại" value={form.phone} onChange={set('phone')} onBlur={blur('phone')}
                error={shown('phone')} inputMode="tel" autoComplete="tel" required placeholder="0901234567"
              />
              <Field
                className="co-fields__wide" id="co-email" label="Email nhận xác nhận đơn" type="email" value={form.email}
                onChange={set('email')} onBlur={blur('email')} error={shown('email')} autoComplete="email" required
                placeholder="ten@example.com"
              />
              <Field
                as="select" id="co-province" label="Tỉnh/Thành" value={form.province} onChange={set('province')}
                onBlur={blur('province')} error={shown('province')} autoComplete="address-level1" required
              >
                <option value="">Chọn tỉnh/thành</option>
                {provinces.map((p) => <option key={p.id} value={p.province}>{p.province}</option>)}
              </Field>
              <Field
                id="co-address" label="Địa chỉ" value={form.address} onChange={set('address')} onBlur={blur('address')}
                error={shown('address')} maxLength={300} autoComplete="street-address" required
                placeholder="Số nhà, đường, phường/xã"
              />
              <Field
                className="co-fields__wide" as="textarea" id="co-note" label="Ghi chú" optional value={form.note}
                onChange={set('note')} maxLength={500} rows={2} placeholder="Ví dụ: giao giờ hành chính"
              />
            </div>
          </section>

          <section className="co-card" aria-labelledby="co-pay-title">
            <h2 id="co-pay-title" className="co-card__title"><span className="co-card__num">2</span>Phương thức thanh toán</h2>
            <RadioCards
              className="co-radios"
              label="Phương thức thanh toán"
              value={form.paymentMethod}
              onChange={(paymentMethod) => setForm((f) => ({ ...f, paymentMethod }))}
              options={PAYMENT_OPTIONS}
              disabled={busy}
            />
          </section>
        </div>

        <aside className="co-summary" aria-labelledby="co-summary-title">
          <h2 id="co-summary-title" className="co-summary__title">Đơn hàng của bạn</h2>
          <ul className="co-items">
            {cart.items.map((i) => (
              <li key={i.variantId} className="co-item">
                <Thumb item={i} />
                <div className="co-item__info">
                  <p className="co-item__name">{i.productName}</p>
                  <p className="co-item__variant">{i.size} · {i.color} · SL {i.quantity}</p>
                </div>
                <span className="co-item__total tabular">{formatVnd(i.lineTotal)}</span>
              </li>
            ))}
          </ul>
          <dl className="co-rows">
            <div><dt>Tạm tính</dt><dd className="tabular">{formatVnd(cart.subtotal)}</dd></div>
            <div>
              <dt>Phí vận chuyển</dt>
              <dd>{form.province ? <Money value={shippingFee} /> : <span className="co-rows__hint">Chọn tỉnh/thành</span>}</dd>
            </div>
          </dl>
          <div className="co-total">
            <span>Tổng cộng</span>
            <Money value={cart.subtotal + shippingFee} className="co-total__value" />
          </div>
          <Button
            type="submit"
            size="lg"
            block
            loading={busy}
            disabled={hasUnavailable}
            aria-describedby={hasUnavailable ? 'co-block-note' : undefined}
            iconLeft={<LockIcon size={18} />}
          >
            {busy ? 'Đang xử lý...' : isMomo ? 'Thanh toán với MoMo' : 'Đặt hàng (COD)'}
          </Button>
          {hasUnavailable && <p id="co-block-note" className="co-note">Hãy chỉnh lại giỏ hàng để tiếp tục.</p>}
          <p className="co-note">
            <ShieldIcon size={16} />
            <span>{isMomo ? 'Bạn sẽ được chuyển sang MoMo để hoàn tất thanh toán.' : 'Bạn thanh toán khi nhận hàng.'} Giá cuối cùng được máy chủ tính lại khi đặt hàng.</span>
          </p>
        </aside>
      </form>
    );
  }

  return (
    <div className="container co">
      <header className="co__head">
        <p className="eyebrow">Thanh toán</p>
        <h1>Hoàn tất đơn hàng</h1>
        <ProgressSteps steps={steps} label="Các bước thanh toán" className="co__steps" />
      </header>
      {content}
    </div>
  );
}
