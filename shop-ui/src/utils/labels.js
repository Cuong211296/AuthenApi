export const ORDER_STATUS_LABEL = {
  PENDING_PAYMENT: 'Chờ thanh toán',
  PENDING_CONFIRM: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  SHIPPING: 'Đang giao',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã huỷ',
};

export const PAYMENT_STATUS_LABEL = {
  UNPAID: 'Chưa thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  EXPIRED: 'Hết hạn thanh toán',
};

/** Mirrors OrderStatus.canTransitionTo on the server. */
export const NEXT_STATUSES = {
  PENDING_PAYMENT: ['CANCELLED'],
  PENDING_CONFIRM: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPING', 'CANCELLED'],
  SHIPPING: ['COMPLETED'],
  COMPLETED: [],
  CANCELLED: [],
};
