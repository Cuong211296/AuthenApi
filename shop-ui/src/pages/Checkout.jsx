import { useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client.js';
import { useCart } from '../context/CartContext.jsx';
import { useAnimatedNumber } from '../hooks/useAnimatedNumber.js';
import { useGhnAddress } from '../hooks/useGhnAddress.js';
import { useShippingConfig } from '../hooks/useShippingConfig.js';
import { useShippingQuote } from '../hooks/useShippingQuote.js';
import {
  EMPTY_SELECTION, MODE_IDS, MODE_TEXT, addressModeFromConfig, buildAddressPayload, findOption, isAddressComplete,
  selectionReducer, toOptions,
} from '../utils/address.js';
import { formatVnd } from '../utils/money.js';
import { checkoutErrors, normalizeCheckoutForm } from '../utils/checkout.js';
import {
  NOT_DELIVERABLE_MESSAGE, cartKeyOf, formatWeight, orderTotal, shippingDisplay, shippingHint,
} from '../utils/shipping.js';
import Badge from '../components/ui/Badge.jsx';
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

const FIELD_ORDER = ['receiverName', 'phone', 'email', 'province', 'district', 'ward', 'address'];
const FALLBACK_NOTE = 'Không tải được danh sách địa chỉ, hãy nhập phường/xã thủ công';

/** Inline load failure of a select (announced as an alert) with a retry button; used as the field's hint. */
function LoadError({ what, onRetry }) {
  return (
    <span className="co-loaderr" role="alert">
      <span>Không tải được {what}.</span>
      <button type="button" className="co-loaderr__retry" onClick={onRetry}>Thử lại</button>
    </span>
  );
}

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
  const { config, loading: configLoading } = useShippingConfig();
  const [selections, dispatchSel] = useReducer(selectionReducer, EMPTY_SELECTION);
  const ghn = useGhnAddress({
    enabled: config.addressMode === MODE_IDS,
    provinceId: selections.province?.id,
    districtId: selections.district?.id,
  });
  const provincesFailed = ghn.provinces.status === 'error';
  const mode = addressModeFromConfig(config, provincesFailed);
  const textFallback = config.addressMode === MODE_IDS && mode === MODE_TEXT;
  const addr = useMemo(() => ({ mode, selections }), [mode, selections]);
  const provinceOptions = useMemo(() => toOptions(ghn.provinces.items, 'id'), [ghn.provinces.items]);
  const districtOptions = useMemo(() => toOptions(ghn.districts.items, 'id'), [ghn.districts.items]);
  const wardOptions = useMemo(() => toOptions(ghn.wards.items, 'code'), [ghn.wards.items]);
  const [form, setForm] = useState({ receiverName: '', phone: '', email: '', address: '', ward: '', province: '', note: '', paymentMethod: 'MOMO' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false); // guards double submits that arrive before `busy` re-renders
  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const set = (key) => (e) => { const { value } = e.target; setForm((f) => ({ ...f, [key]: value })); };
  const blur = (key) => () => setTouched((t) => ({ ...t, [key]: true }));

  const [loaded, setLoaded] = useState(false);
  const [placed, setPlaced] = useState(false);

  // The fixed-table province list is only needed for the text address (also the fallback when GHN data is down).
  useEffect(() => {
    if (mode !== MODE_TEXT || configLoading) return undefined;
    let ignore = false;
    api('GET', '/shipping/provinces', undefined, { auth: false })
      .then((list) => { if (!ignore) setProvinces(list); })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [mode, configLoading]);

  // Refresh availability/stock before the customer submits.
  useEffect(() => {
    let ignore = false;
    reload().finally(() => { if (!ignore) setLoaded(true); });
    return () => { ignore = true; };
  }, [reload]);

  const tableFee = useMemo(() => provinces.find((p) => p.province === form.province)?.fee ?? 0, [provinces, form.province]);
  const addressPayload = buildAddressPayload({ mode, form, selections });
  const quote = useShippingQuote({
    address: addressPayload,
    cartKey: cartKeyOf(cart),
    enabled: loaded && !configLoading && !placed && cart.items.length > 0 && isAddressComplete(mode, form, selections),
  });
  const ship = shippingDisplay({ state: quote.state, quote: quote.quote, tableFee, province: form.province, mode });
  const shippingFee = ship?.fee ?? 0;
  // The address the server rejected at checkout (2018); the block lifts as soon as the address changes.
  const addressKey = JSON.stringify(addressPayload);
  const [rejectedKey, setRejectedKey] = useState('');
  const notDeliverable = (quote.state === 'ready' && quote.quote?.deliverable === false) || rejectedKey === addressKey;
  const hasUnavailable = cart.items.some((i) => !i.available);
  const errors = useMemo(() => checkoutErrors(form, addr), [form, addr]);
  const shown = (key) => (submitted || touched[key] ? errors[key] : undefined);

  async function submit(e) {
    e.preventDefault();
    if (busy || inFlight.current || notDeliverable) return;
    setError('');
    setSubmitted(true);
    const firstInvalid = FIELD_ORDER.find((key) => errors[key]);
    if (firstInvalid) {
      document.getElementById(`co-${firstInvalid}`)?.focus();
      return;
    }
    const payload = normalizeCheckoutForm(form, addr);
    inFlight.current = true;
    setBusy(true);
    let order;
    try {
      order = await api('POST', '/orders', payload);
    } catch (err) {
      inFlight.current = false;
      setBusy(false);
      if (err.code === 2018) {
        setRejectedKey(addressKey);
        return document.getElementById('co-ward')?.focus();
      }
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
  } else if (!loaded || configLoading) {
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
              {mode === MODE_IDS ? (
                <>
                  <Field
                    as="select" id="co-province" label="Tỉnh/Thành" autoComplete="address-level1" required
                    value={selections.province ? String(selections.province.id) : ''}
                    disabled={ghn.provinces.status === 'loading'}
                    onChange={(e) => dispatchSel({ type: 'province', option: findOption(provinceOptions, e.target.value) })}
                    onBlur={blur('province')} error={shown('province')}
                  >
                    <option value="">{ghn.provinces.status === 'loading' ? 'Đang tải...' : 'Chọn tỉnh/thành'}</option>
                    {provinceOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </Field>
                  <Field
                    as="select" id="co-district" label="Quận/Huyện" autoComplete="address-level2" required
                    value={selections.district ? String(selections.district.id) : ''}
                    disabled={!selections.province || ghn.districts.status !== 'ready'}
                    onChange={(e) => dispatchSel({ type: 'district', option: findOption(districtOptions, e.target.value) })}
                    onBlur={blur('district')} error={shown('district')}
                    hint={ghn.districts.status === 'error' ? <LoadError what="danh sách quận/huyện" onRetry={ghn.districts.retry} /> : undefined}
                  >
                    <option value="">{ghn.districts.status === 'loading' ? 'Đang tải...' : 'Chọn quận/huyện'}</option>
                    {districtOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </Field>
                  <Field
                    className="co-fields__wide" as="select" id="co-ward" label="Phường/Xã" autoComplete="address-level3" required
                    value={selections.ward ? selections.ward.code : ''}
                    disabled={!selections.district || ghn.wards.status !== 'ready'}
                    onChange={(e) => dispatchSel({ type: 'ward', option: findOption(wardOptions, e.target.value) })}
                    onBlur={blur('ward')} error={shown('ward')}
                    hint={ghn.wards.status === 'error' ? <LoadError what="danh sách phường/xã" onRetry={ghn.wards.retry} /> : undefined}
                  >
                    <option value="">{ghn.wards.status === 'loading' ? 'Đang tải...' : 'Chọn phường/xã'}</option>
                    {wardOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </Field>
                </>
              ) : (
                <>
                  <Field
                    as="select" id="co-province" label="Tỉnh/Thành" value={form.province} onChange={set('province')}
                    onBlur={blur('province')} error={shown('province')} autoComplete="address-level1" required
                  >
                    <option value="">Chọn tỉnh/thành</option>
                    {provinces.map((p) => <option key={p.id} value={p.province}>{p.province}</option>)}
                  </Field>
                  <Field
                    id="co-ward" label="Phường/Xã" value={form.ward} onChange={set('ward')} onBlur={blur('ward')}
                    error={shown('ward')} maxLength={100} autoComplete="address-level2" required placeholder="Ví dụ: Phường 14"
                  />
                  {textFallback && (
                    <p className="co-alert co-alert--info co-fields__wide" role="status">
                      <AlertIcon size={18} /><span>{FALLBACK_NOTE}</span>
                    </p>
                  )}
                </>
              )}
              <Field
                className="co-fields__wide" id="co-address" label="Địa chỉ" value={form.address} onChange={set('address')}
                onBlur={blur('address')} error={shown('address')} maxLength={300} autoComplete="street-address" required
                placeholder="Số nhà, tên đường"
              />
              {notDeliverable && (
                <p className="co-alert co-alert--error co-fields__wide" role="alert">
                  <AlertIcon size={18} /><span>{NOT_DELIVERABLE_MESSAGE}</span>
                </p>
              )}
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
              <dd aria-busy={ship?.busy ? true : undefined}>
                {ship?.pending ? (
                  <span className="co-rows__hint" role="status">Đang tính phí...</span>
                ) : ship ? (
                  <>
                    <span className={`co-fee ${ship.busy ? 'is-busy' : ''}`}>
                      <Badge tone={ship.tone}>{ship.label}</Badge>
                      {ship.unknownFee
                        ? <span className="tabular co-fee__amount">—</span>
                        : <Money value={shippingFee} className="co-fee__amount" />}
                    </span>
                    {(ship.note || ship.weightGrams) && (
                      <span className="co-fee__note">
                        {ship.note && <span>{ship.note}</span>}
                        {formatWeight(ship.weightGrams) && <span>Khối lượng ước tính: {formatWeight(ship.weightGrams)}</span>}
                      </span>
                    )}
                    <span className="sr-only" role="status">
                      {ship.busy ? 'Đang tính phí vận chuyển' : ship.unknownFee ? ship.note : `Phí vận chuyển ${formatVnd(shippingFee)}, ${ship.label}`}
                    </span>
                  </>
                ) : <span className="co-rows__hint">{shippingHint(mode)}</span>}
              </dd>
            </div>
          </dl>
          <div className="co-total">
            <span>Tổng cộng</span>
            <Money value={orderTotal(cart.subtotal, shippingFee)} className="co-total__value" />
          </div>
          <Button
            type="submit"
            size="lg"
            block
            loading={busy}
            disabled={hasUnavailable || notDeliverable}
            aria-describedby={hasUnavailable || notDeliverable ? 'co-block-note' : undefined}
            iconLeft={<LockIcon size={18} />}
          >
            {busy ? 'Đang xử lý...' : isMomo ? 'Thanh toán với MoMo' : 'Đặt hàng (COD)'}
          </Button>
          {(hasUnavailable || notDeliverable) && (
            <p id="co-block-note" className="co-note">
              {hasUnavailable ? 'Hãy chỉnh lại giỏ hàng để tiếp tục.' : 'Hãy kiểm tra lại địa chỉ giao hàng để tiếp tục.'}
            </p>
          )}
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
