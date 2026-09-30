import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { api } from '../api/client.js';
import Badge from '../components/ui/Badge.jsx';
import Button from '../components/ui/Button.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import Pager from '../components/ui/Pager.jsx';
import { Skeleton } from '../components/ui/Skeleton.jsx';
import { AlertIcon, ArrowIcon, BagIcon, ChevronIcon } from '../components/ui/icons.jsx';
import { fadeUp, stagger } from '../motion/variants.js';
import { formatVnd } from '../utils/money.js';
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE, PAYMENT_STATUS_LABEL, PAYMENT_STATUS_TONE } from '../utils/labels.js';
import './Orders.css';

const listVariants = stagger(0, 0.06);

function itemsSummary(items = []) {
  if (items.length === 0) return '';
  const first = items[0];
  const more = items.length - 1;
  return `${first.productName} (${first.size} / ${first.color}) × ${first.quantity}${more > 0 ? ` và ${more} sản phẩm khác` : ''}`;
}

function OrdersSkeleton() {
  return (
    <div className="or__list" role="status" aria-label="Đang tải đơn hàng">
      {[0, 1, 2].map((i) => (
        <div key={i} className="oc oc--skel" aria-hidden="true">
          <div className="oc__main">
            <Skeleton variant="line" width="45%" height={18} />
            <Skeleton variant="line" width="30%" height={12} />
            <Skeleton variant="line" width="70%" />
          </div>
          <Skeleton variant="line" width={90} height={24} />
        </div>
      ))}
    </div>
  );
}

export default function Orders() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [shownPage, setShownPage] = useState(0);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const headRef = useRef(null);

  useEffect(() => {
    let ignore = false;
    setError('');
    api('GET', `/orders?page=${page}&size=10`)
      .then((d) => { if (!ignore) { setData(d); setShownPage(page); } })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [page, attempt]);

  function go(next) {
    setPage(next);
    headRef.current?.scrollIntoView({ block: 'start' });
  }

  let content;
  if (error && !data) {
    content = (
      <EmptyState
        tone="danger"
        role="alert"
        icon={<AlertIcon size={28} />}
        title="Không tải được đơn hàng"
        action={<Button variant="dark" onClick={() => setAttempt((a) => a + 1)}>Thử lại</Button>}
      >
        {error}
      </EmptyState>
    );
  } else if (!data) {
    content = <OrdersSkeleton />;
  } else if (data.items.length === 0) {
    content = (
      <EmptyState
        icon={<BagIcon size={28} />}
        title="Bạn chưa có đơn hàng nào"
        action={<Button as={Link} to="/" variant="dark" iconRight={<ArrowIcon size={18} />}>Bắt đầu mua sắm</Button>}
      >
        Khi bạn đặt hàng, đơn sẽ xuất hiện ở đây để bạn theo dõi tiến trình.
      </EmptyState>
    );
  } else {
    content = (
      <>
        {error && <p className="or__error" role="alert">{error}</p>}
        <motion.ul
          key={shownPage}
          className={`or__list ${page !== shownPage ? 'is-stale' : ''}`}
          variants={listVariants}
          initial="hidden"
          animate="show"
        >
          {data.items.map((o) => (
            <motion.li key={o.code} variants={fadeUp} className="or__item">
              <Link to={`/orders/${o.code}`} className="oc">
                <div className="oc__main">
                  <p className="oc__code tabular">{o.code}</p>
                  <p className="oc__date">{new Date(o.createdAt).toLocaleString('vi-VN')}</p>
                  <p className="oc__items">{itemsSummary(o.items)}</p>
                  <p className="oc__badges">
                    <Badge tone={ORDER_STATUS_TONE[o.status] ?? 'neutral'} dot>{ORDER_STATUS_LABEL[o.status] ?? o.status}</Badge>
                    <Badge tone={PAYMENT_STATUS_TONE[o.paymentStatus] ?? 'neutral'}>{PAYMENT_STATUS_LABEL[o.paymentStatus] ?? o.paymentStatus}</Badge>
                  </p>
                </div>
                <div className="oc__side">
                  <span className="oc__total tabular">{formatVnd(o.total)}</span>
                  <span className="oc__go" aria-hidden="true"><ChevronIcon direction="right" size={18} /></span>
                </div>
              </Link>
            </motion.li>
          ))}
        </motion.ul>
        <Pager page={page} totalPages={data.totalPages} onChange={go} label="Phân trang đơn hàng" />
      </>
    );
  }

  return (
    <div className="container or">
      <header className="or__head" ref={headRef}>
        <p className="eyebrow">Tài khoản</p>
        <h1>Đơn hàng của tôi</h1>
      </header>
      {content}
    </div>
  );
}
