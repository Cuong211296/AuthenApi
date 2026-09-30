import { paymentView } from './paymentView.js';

describe('paymentView', () => {
  it('loading / error without a result', () => {
    expect(paymentView({ result: null, error: '' })).toBe('loading');
    expect(paymentView({ result: null, error: 'x' })).toBe('error');
  });
  it('paid wins, then cancelled, then failed', () => {
    expect(paymentView({ result: { paymentStatus: 'PAID', orderStatus: 'CANCELLED' } })).toBe('paid');
    expect(paymentView({ result: { paymentStatus: 'EXPIRED', orderStatus: 'CANCELLED' } })).toBe('cancelled');
    expect(paymentView({ result: { paymentStatus: 'FAILED', orderStatus: 'PENDING_PAYMENT' } })).toBe('failed');
  });
  it('pending only for an unpaid PENDING_PAYMENT order', () => {
    expect(paymentView({ result: { paymentStatus: 'UNPAID', orderStatus: 'PENDING_PAYMENT' } })).toBe('pending');
    expect(paymentView({ result: { paymentStatus: 'UNPAID', orderStatus: 'PENDING_CONFIRM' } })).toBe('unknown');
    expect(paymentView({ result: { paymentStatus: 'UNPAID', orderStatus: 'CONFIRMED' } })).toBe('unknown');
  });
});
