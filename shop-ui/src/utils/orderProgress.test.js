import { orderProgress } from './orderProgress.js';

const states = (status) => orderProgress(status).steps.map((s) => s.state);

describe('orderProgress', () => {
  it('starts at "Chờ thanh toán" for unpaid MoMo orders', () => {
    const p = orderProgress('PENDING_PAYMENT');
    expect(p.steps[0]).toMatchObject({ key: 'PENDING_PAYMENT', label: 'Chờ thanh toán' });
    expect(states('PENDING_PAYMENT')).toEqual(['current', 'todo', 'todo', 'todo']);
  });

  it('starts at "Chờ xác nhận" for orders waiting on the shop', () => {
    expect(orderProgress('PENDING_CONFIRM').steps[0].label).toBe('Chờ xác nhận');
    expect(states('PENDING_CONFIRM')).toEqual(['current', 'todo', 'todo', 'todo']);
  });

  it('marks earlier steps done and later steps todo', () => {
    expect(states('CONFIRMED')).toEqual(['done', 'current', 'todo', 'todo']);
    expect(states('SHIPPING')).toEqual(['done', 'done', 'current', 'todo']);
  });

  it('marks every step done when completed', () => {
    expect(states('COMPLETED')).toEqual(['done', 'done', 'done', 'done']);
    expect(orderProgress('COMPLETED').currentIndex).toBe(3);
  });

  it('shows cancelled orders as cancelled with no progress', () => {
    const p = orderProgress('CANCELLED');
    expect(p.cancelled).toBe(true);
    expect(p.currentIndex).toBe(-1);
    expect(states('CANCELLED')).toEqual(['todo', 'todo', 'todo', 'todo']);
  });

  it('does not crash on an unknown status', () => {
    expect(orderProgress('WAT').cancelled).toBe(false);
    expect(states('WAT')).toEqual(['todo', 'todo', 'todo', 'todo']);
  });
});
