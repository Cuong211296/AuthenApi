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

/** Badge tones (components/ui/Badge) per status; the label text is always shown too, never color alone. */
export const ORDER_STATUS_TONE = {
  PENDING_PAYMENT: 'warn',
  PENDING_CONFIRM: 'info',
  CONFIRMED: 'info',
  SHIPPING: 'accent',
  COMPLETED: 'success',
  CANCELLED: 'danger',
};

export const PAYMENT_STATUS_TONE = {
  UNPAID: 'neutral',
  PAID: 'success',
  FAILED: 'danger',
  EXPIRED: 'danger',
};

export const PAYMENT_METHOD_LABEL = {
  MOMO: 'MoMo',
  COD: 'Thanh toán khi nhận hàng (COD)',
};

/** Button text for moving an order to a status (admin). */
export const ORDER_ACTION_LABEL = {
  CONFIRMED: 'Xác nhận đơn',
  SHIPPING: 'Giao hàng',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Huỷ đơn',
};
