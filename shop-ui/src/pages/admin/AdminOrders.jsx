import { Fragment, useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { api } from '../../api/client.js';
import Badge from '../../components/ui/Badge.jsx';
import Button from '../../components/ui/Button.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import Modal from '../../components/ui/Modal.jsx';
import Pager from '../../components/ui/Pager.jsx';
import Tabs from '../../components/ui/Tabs.jsx';
import { useToast } from '../../components/ui/Toast.jsx';
import { AlertIcon, ChevronIcon, ReceiptIcon } from '../../components/ui/icons.jsx';
import { formatVnd } from '../../utils/money.js';
import { formatWeight, shippingSourceLabel } from '../../utils/shipping.js';
import {
  NEXT_STATUSES, ORDER_ACTION_LABEL, ORDER_STATUS_LABEL, ORDER_STATUS_TONE,
  PAYMENT_METHOD_LABEL, PAYMENT_STATUS_LABEL, PAYMENT_STATUS_TONE,
} from '../../utils/labels.js';
import AdminPageHeader from './AdminPageHeader.jsx';
import { SkeletonRows } from './AdminParts.jsx';

const buildPath = (status, page) => {
  const query = new URLSearchParams({ page: String(page), size: '20' });
  if (status) query.set('status', status);
  return `/admin/orders?${query}`;
};

const STATUS_TABS = [{ value: '', label: 'Tất cả' }, ...Object.entries(ORDER_STATUS_LABEL).map(([value, label]) => ({ value, label }))];
const fmtDate = (iso) => (iso ? new Date(iso).toLocaleString('vi-VN') : '—');

function OrderDetail({ order, id }) {
  const source = shippingSourceLabel(order.shippingSource);
  const weight = formatWeight(order.weightGrams);
  return (
    <tr className="ad-detail-row">
      <td colSpan={5}>
        <motion.div id={id} className="ad-detail" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.25 }}>
          <div>
            <h3>Sản phẩm</h3>
            <ul className="ad-items">
              {(order.items || []).map((it, i) => (
                <li key={i}>
                  <span className="ad-items__name">
                    {it.productName}
                    <span className="ad-items__sub">{it.size} / {it.color} · {formatVnd(it.unitPrice)} × {it.quantity}</span>
                  </span>
                  <span className="ad-num ad-strong">{formatVnd(it.lineTotal)}</span>
                </li>
              ))}
            </ul>
            <div className="ad-totals">
              <div><span>Tạm tính</span><span className="ad-num">{formatVnd(order.subtotal)}</span></div>
              <div><span>Phí vận chuyển</span><span className="ad-num">{formatVnd(order.shippingFee)}</span></div>
              <div className="is-total"><span>Tổng cộng</span><span className="ad-num">{formatVnd(order.total)}</span></div>
            </div>
          </div>
          <div>
            <h3>Người nhận</h3>
            <dl>
              <dt>Họ tên</dt><dd>{order.receiverName}</dd>
              <dt>Điện thoại</dt><dd>{order.phone}</dd>
              {order.email && (<><dt>Email</dt><dd>{order.email}</dd></>)}
              <dt>Địa chỉ</dt><dd>{order.address}</dd>
              {order.ward && (<><dt>Phường/Xã</dt><dd>{order.ward}</dd></>)}
              {order.district && (<><dt>Quận/Huyện</dt><dd>{order.district}</dd></>)}
              <dt>Tỉnh/Thành</dt><dd>{order.province}</dd>
              {order.note && (<><dt>Ghi chú</dt><dd>{order.note}</dd></>)}
            </dl>
          </div>
          <div>
            <h3>Thanh toán</h3>
            <dl>
              <dt>Phương thức</dt><dd>{PAYMENT_METHOD_LABEL[order.paymentMethod] || order.paymentMethod}</dd>
              <dt>Trạng thái</dt><dd>{PAYMENT_STATUS_LABEL[order.paymentStatus] || order.paymentStatus}</dd>
              {source && (<><dt>Nguồn phí ship</dt><dd><Badge tone={source.tone}>{source.label}</Badge></dd></>)}
              {weight && (<><dt>Khối lượng</dt><dd>{weight}</dd></>)}
              <dt>Đặt lúc</dt><dd>{fmtDate(order.createdAt)}</dd>
              {order.paidAt && (<><dt>Đã trả lúc</dt><dd>{fmtDate(order.paidAt)}</dd></>)}
              {order.expiresAt && order.paymentStatus === 'UNPAID' && (<><dt>Hết hạn</dt><dd>{fmtDate(order.expiresAt)}</dd></>)}
            </dl>
          </div>
        </motion.div>
      </td>
    </tr>
  );
}

export default function AdminOrders() {
  const { toast } = useToast();
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [loaded, setLoaded] = useState(null); // { key, data }
  const [error, setError] = useState('');
  const [tick, setTick] = useState(0);
  const [expanded, setExpanded] = useState('');
  const [confirm, setConfirm] = useState({ code: '', open: false }); // order awaiting cancel confirmation
  const [busy, setBusy] = useState('');

  const key = `${status}|${page}`;
  useEffect(() => {
    let ignore = false;
    api('GET', buildPath(status, page))
      .then((d) => { if (!ignore) { setLoaded({ key: `${status}|${page}`, data: d }); setError(''); } })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, [status, page, tick]);

  const data = loaded?.data;
  const stale = loaded && loaded.key !== key;

  async function change(code, next) {
    setBusy(code);
    try {
      await api('PUT', `/admin/orders/${code}/status`, { status: next });
      toast(`Đơn ${code}: ${ORDER_STATUS_LABEL[next]}`, { tone: 'success' });
    } catch (e) {
      toast(e.message, { tone: 'danger' });
    } finally {
      setBusy('');
      setConfirm((c) => ({ ...c, open: false }));
      setTick((t) => t + 1); // reload even after a failure so stale action buttons disappear
    }
  }

  const onAction = (code, next) => (next === 'CANCELLED' ? setConfirm({ code, open: true }) : change(code, next));

  let body;
  if (error && !data) {
    body = (
      <EmptyState
        role="alert"
        tone="danger"
        icon={<AlertIcon size={26} />}
        title="Không tải được đơn hàng"
        action={<Button variant="dark" onClick={() => { setError(''); setTick((t) => t + 1); }}>Thử lại</Button>}
      >
        {error}
      </EmptyState>
    );
  } else if (!data) {
    body = <div className="ad-card"><SkeletonRows label="Đang tải đơn hàng" /></div>;
  } else if (data.items.length === 0) {
    body = (
      <EmptyState icon={<ReceiptIcon size={26} />} title="Chưa có đơn hàng">
        {status ? `Không có đơn nào ở trạng thái “${ORDER_STATUS_LABEL[status]}”.` : 'Đơn hàng mới sẽ xuất hiện tại đây.'}
      </EmptyState>
    );
  } else {
    body = (
      <div className={`ad-card ad-stale ${stale ? 'is-stale' : ''}`} aria-busy={stale || undefined}>
        <table className="ad-table ad-table--cards">
          <thead>
            <tr>
              <th scope="col">Mã đơn</th>
              <th scope="col">Khách</th>
              <th scope="col">Trạng thái</th>
              <th scope="col" className="is-right">Tổng</th>
              <th scope="col" className="is-right">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((o) => {
              const open = expanded === o.code;
              const detailId = `ad-order-${o.code}`;
              return (
                <Fragment key={o.code}>
                  <tr className={`ad-orow ${open ? 'is-open' : ''}`}>
                    <td>
                      <button type="button" className="ad-linkbtn ad-expand" aria-expanded={open} aria-controls={open ? detailId : undefined} onClick={() => setExpanded(open ? '' : o.code)}>
                        {o.code}
                        <ChevronIcon size={16} aria-hidden="true" />
                        <span className="sr-only">{open ? 'Ẩn chi tiết' : 'Xem chi tiết'}</span>
                      </button>
                      <div className="ad-muted">{fmtDate(o.createdAt)}</div>
                    </td>
                    <td data-label="Khách">
                      <div className="ad-strong">{o.receiverName}</div>
                      <div className="ad-muted">{o.phone} · {o.province}</div>
                    </td>
                    <td data-label="Trạng thái">
                      <div className="ad-badges">
                        <Badge tone={ORDER_STATUS_TONE[o.status] || 'neutral'} dot>{ORDER_STATUS_LABEL[o.status] || o.status}</Badge>
                        <Badge tone={PAYMENT_STATUS_TONE[o.paymentStatus] || 'neutral'}>{PAYMENT_STATUS_LABEL[o.paymentStatus] || o.paymentStatus}</Badge>
                        <Badge>{o.paymentMethod}</Badge>
                      </div>
                    </td>
                    <td className="is-right ad-num ad-strong" data-label="Tổng">{formatVnd(o.total)}</td>
                    <td className="ad-orow__actions">
                      <div className="ad-cell-actions">
                        {(NEXT_STATUSES[o.status] || []).map((n) => (
                          <Button
                            key={n}
                            size="sm"
                            variant={n === 'CANCELLED' ? 'danger' : 'dark'}
                            loading={busy === o.code && !confirm.open}
                            disabled={busy !== ''}
                            aria-label={`${ORDER_ACTION_LABEL[n]} ${o.code}`}
                            onClick={() => onAction(o.code, n)}
                          >
                            {ORDER_ACTION_LABEL[n]}
                          </Button>
                        ))}
                      </div>
                    </td>
                  </tr>
                  {open && <OrderDetail order={o} id={detailId} />}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <>
      <AdminPageHeader
        title="Đơn hàng"
        description={data ? `${data.totalElements} đơn${status ? ` · ${ORDER_STATUS_LABEL[status]}` : ''}` : 'Theo dõi và cập nhật trạng thái đơn hàng.'}
      />
      <div className="ad-tabs">
        <Tabs items={STATUS_TABS} value={status} onChange={(v) => { setStatus(v); setPage(0); setExpanded(''); }} label="Lọc theo trạng thái đơn" />
      </div>
      {error && data && <p className="ad-error" role="alert">{error}</p>}
      {body}
      <div className="ad-pager">
        {data && <Pager page={page} totalPages={data.totalPages} onChange={(p) => { setPage(p); setExpanded(''); }} label="Phân trang đơn hàng" />}
      </div>

      <Modal open={confirm.open} onClose={() => busy === '' && setConfirm((c) => ({ ...c, open: false }))} title={`Huỷ đơn ${confirm.code}?`}>
        <p>Huỷ đơn <strong>{confirm.code}</strong>? Kho sẽ được hoàn lại và không thể hoàn tác.</p>
        <div className="ad-confirm-actions">
          <Button variant="ghost" onClick={() => setConfirm((c) => ({ ...c, open: false }))} disabled={busy !== ''}>Giữ đơn</Button>
          <Button variant="danger" className="ad-solid-danger" loading={busy !== ''} onClick={() => change(confirm.code, 'CANCELLED')}>Huỷ đơn</Button>
        </div>
      </Modal>
    </>
  );
}
