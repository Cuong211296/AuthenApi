import { ORDER_STATUS_LABEL } from './labels.js';

/**
 * Maps an order status to the customer-facing timeline:
 * (Chờ thanh toán | Chờ xác nhận) -> Đã xác nhận -> Đang giao -> Hoàn thành.
 * Each step has state 'done' | 'current' | 'todo'. A finished order has every step 'done'.
 * CANCELLED (or an unknown status) has no progress: `cancelled` is true only for CANCELLED and every step is 'todo'.
 */
export function orderProgress(status) {
  const first = status === 'PENDING_PAYMENT' ? 'PENDING_PAYMENT' : 'PENDING_CONFIRM';
  const keys = [first, 'CONFIRMED', 'SHIPPING', 'COMPLETED'];
  const index = keys.indexOf(status);
  const steps = keys.map((key, i) => ({
    key,
    label: ORDER_STATUS_LABEL[key],
    state: index < 0 ? 'todo' : status === 'COMPLETED' || i < index ? 'done' : i === index ? 'current' : 'todo',
  }));
  return { cancelled: status === 'CANCELLED', currentIndex: index, steps };
}
