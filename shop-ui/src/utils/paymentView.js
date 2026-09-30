/**
 * Which state the payment result page shows: loading | paid | pending | failed | cancelled | unknown | error.
 * "pending" only applies to an unpaid order that is still PENDING_PAYMENT; any other combination is "unknown"
 * (neutral, with a link to the order) instead of asking the customer to keep checking.
 */
export function paymentView({ result, error }) {
  if (!result) return error ? 'error' : 'loading';
  if (result.paymentStatus === 'PAID') return 'paid';
  if (result.orderStatus === 'CANCELLED') return 'cancelled';
  if (result.paymentStatus === 'FAILED') return 'failed';
  if (result.orderStatus === 'PENDING_PAYMENT') return 'pending';
  return 'unknown';
}
