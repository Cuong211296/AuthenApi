import { formatVnd } from './money.js';

describe('formatVnd', () => {
  it('uses dots as thousands separators and the dong sign', () => {
    expect(formatVnd(150000)).toContain('150.000');
    expect(formatVnd(150000)).toContain('₫');
  });
  it('handles zero and missing values', () => {
    expect(formatVnd(0)).toContain('0');
    expect(formatVnd(undefined)).toContain('0');
  });
});
