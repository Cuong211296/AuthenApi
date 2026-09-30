import { isOrderNotFound } from './orderErrors.js';

describe('isOrderNotFound', () => {
  it('recognises the ORDER_NOT_FOUND code and HTTP 404', () => {
    expect(isOrderNotFound({ code: 2010 })).toBe(true);
    expect(isOrderNotFound({ status: 404 })).toBe(true);
  });
  it('treats other failures as load errors', () => {
    expect(isOrderNotFound({ status: 500 })).toBe(false);
    expect(isOrderNotFound(new Error('Failed to fetch'))).toBe(false);
    expect(isOrderNotFound(null)).toBe(false);
  });
});
