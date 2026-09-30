import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';
import Badge from '../components/ui/Badge.jsx';
import Button from '../components/ui/Button.jsx';
import StatusMark from '../components/ui/StatusMark.jsx';
import { ArrowIcon, RefreshIcon } from '../components/ui/icons.jsx';
import { paymentView } from '../utils/paymentView.js';
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE, PAYMENT_STATUS_LABEL, PAYMENT_STATUS_TONE } from '../utils/labels.js';
import './PaymentResult.css';

const COPY = {
  loading: { mark: 'pending', title: 'Đang xác nhận với MoMo...', text: 'Vui lòng chờ trong giây lát, đừng đóng trang này.' },
  paid: { mark: 'success', title: 'Thanh toán thành công', text: 'Cảm ơn bạn! Email xác nhận sẽ được gửi tới bạn.' },
  pending: {
    mark: 'pending',
    title: 'Đang chờ xác nhận thanh toán',
    text: 'Chưa nhận được xác nhận thanh toán. Nếu bạn đã thanh toán, hãy bấm "Kiểm tra lại" sau vài giây.',
  },
  failed: { mark: 'danger', title: 'Thanh toán không thành công', text: 'Giao dịch chưa hoàn tất. Bạn có thể thử thanh toán lại từ trang đơn hàng.' },
  cancelled: { mark: 'danger', title: 'Đơn hàng đã bị huỷ', text: 'Đơn hàng đã bị huỷ (hết hạn thanh toán).' },
  unknown: { mark: 'warn', title: 'Chưa rõ trạng thái thanh toán', text: 'Hãy xem chi tiết đơn hàng để biết trạng thái mới nhất.' },
  error: { mark: 'warn', title: 'Không thể xác nhận thanh toán', text: '' },
};

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
    setResult(null); // never show a badge that belongs to another order
    check(() => ignore);
    return () => { ignore = true; };
  }, [check]);

  const view = paymentView({ result, error });
  const copy = COPY[view];
  const text = view === 'error' ? error : copy.text;

  return (
    <div className="container pr">
      <section className={`pr__card pr__card--${view}`} aria-labelledby="pr-title">
        <div role="status" className="pr__status">
          <StatusMark key={copy.mark} kind={copy.mark} />
          <p className="eyebrow">Kết quả thanh toán</p>
          <h1 id="pr-title" className="pr__title">{copy.title}</h1>
          {text && <p className="pr__text">{text}</p>}
        </div>

        {orderCode && (
          <p className="pr__code">
            Mã đơn hàng <strong className="tabular">{orderCode}</strong>
          </p>
        )}
        {result && (
          <p className="pr__badges">
            <Badge tone={ORDER_STATUS_TONE[result.orderStatus] ?? 'neutral'} dot>{ORDER_STATUS_LABEL[result.orderStatus] ?? result.orderStatus}</Badge>
            <Badge tone={PAYMENT_STATUS_TONE[result.paymentStatus] ?? 'neutral'} dot>{PAYMENT_STATUS_LABEL[result.paymentStatus] ?? result.paymentStatus}</Badge>
          </p>
        )}
        {error && result && <p className="pr__error" role="alert">{error}</p>}

        <div className="pr__actions">
          {orderCode && (
            <Button as={Link} to={`/orders/${orderCode}`} variant={view === 'paid' ? 'primary' : 'dark'} iconRight={<ArrowIcon size={18} />}>
              Xem đơn hàng
            </Button>
          )}
          {orderCode && (
            <Button variant="ghost" loading={busy} onClick={() => check()} iconLeft={<RefreshIcon size={18} />}>
              Kiểm tra lại
            </Button>
          )}
          {!orderCode && <Button as={Link} to="/orders" variant="dark">Xem đơn hàng của tôi</Button>}
        </div>
      </section>
    </div>
  );
}
