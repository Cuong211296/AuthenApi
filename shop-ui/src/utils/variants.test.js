import { sizesOf, colorsFor, findVariant } from './variants.js';

const variants = [
  { id: '1', size: 'M', color: 'white', stock: 3 },
  { id: '2', size: 'M', color: 'black', stock: 0 },
  { id: '3', size: 'L', color: 'white', stock: 1 },
];

describe('variants helpers', () => {
  it('lists each size once in order of appearance', () => {
    expect(sizesOf(variants)).toEqual(['M', 'L']);
  });
  it('lists colors for a size and flags the ones out of stock', () => {
    expect(colorsFor(variants, 'M')).toEqual([
      { color: 'white', available: true },
      { color: 'black', available: false },
    ]);
  });
  it('finds the exact variant or undefined', () => {
    expect(findVariant(variants, 'L', 'white').id).toBe('3');
    expect(findVariant(variants, 'L', 'black')).toBeUndefined();
  });
});
