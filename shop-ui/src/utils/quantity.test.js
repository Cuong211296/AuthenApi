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

import { maxQuantityFor, stockLevel } from './quantity.js';

describe('maxQuantityFor', () => {
  it('uses the stock when it is below the cap', () => expect(maxQuantityFor(7)).toBe(7));
  it('caps at 99 by default', () => expect(maxQuantityFor(500)).toBe(99));
  it('respects a custom cap', () => expect(maxQuantityFor(500, 10)).toBe(10));
  it('is 0 for no stock or bad input', () => {
    expect(maxQuantityFor(0)).toBe(0);
    expect(maxQuantityFor(-3)).toBe(0);
    expect(maxQuantityFor(undefined)).toBe(0);
  });
});

describe('stockLevel', () => {
  it('classifies stock', () => {
    expect(stockLevel(0)).toBe('out');
    expect(stockLevel(undefined)).toBe('out');
    expect(stockLevel(1)).toBe('low');
    expect(stockLevel(4)).toBe('low');
    expect(stockLevel(5)).toBe('ok');
  });
});
