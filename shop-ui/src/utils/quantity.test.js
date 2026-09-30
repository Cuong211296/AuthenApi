import { clampQuantity } from './quantity.js';

describe('clampQuantity', () => {
  it('keeps values inside the range', () => {
    expect(clampQuantity(3, 1, 10)).toBe(3);
  });
  it('clamps to the bounds', () => {
    expect(clampQuantity(0, 1, 10)).toBe(1);
    expect(clampQuantity(-5, 1, 10)).toBe(1);
    expect(clampQuantity(120, 1, 99)).toBe(99);
  });
  it('floors decimals and parses numeric strings', () => {
    expect(clampQuantity(2.9, 1, 10)).toBe(2);
    expect(clampQuantity('7', 1, 10)).toBe(7);
  });
  it('falls back to min for empty or non-numeric input', () => {
    expect(clampQuantity('', 1, 10)).toBe(1);
    expect(clampQuantity('abc', 1, 10)).toBe(1);
    expect(clampQuantity(undefined, 2, 10)).toBe(2);
  });
  it('defaults to 1..99', () => {
    expect(clampQuantity(500)).toBe(99);
  });
});
