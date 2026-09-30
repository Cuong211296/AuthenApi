import ProgressSteps from '../ui/ProgressSteps.jsx';
import StatusMark from '../ui/StatusMark.jsx';
import { orderProgress } from '../../utils/orderProgress.js';
import './OrderTimeline.css';

/**
 * Order progress: Chờ thanh toán / Chờ xác nhận -> Đã xác nhận -> Đang giao -> Hoàn thành.
 * A cancelled order is shown as a distinct notice instead of a timeline. Payment status is shown elsewhere.
 */
export default function OrderTimeline({ status }) {
  const { cancelled, steps } = orderProgress(status);
  if (cancelled) {
    return (
      <div className="ot-cancelled" role="status">
        <StatusMark kind="danger" size={52} />
        <div>
          <p className="ot-cancelled__title">Đơn hàng đã bị huỷ</p>
          <p className="ot-cancelled__text">Đơn hàng này không còn được xử lý. Sản phẩm đã được hoàn lại kho.</p>
        </div>
      </div>
    );
  }
  return <ProgressSteps steps={steps} label="Tiến trình đơn hàng" className="ot" />;
}
