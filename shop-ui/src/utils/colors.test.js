import { colorSwatch } from './colors.js';

describe('colorSwatch', () => {
  it('maps English names used by the catalog', () => {
    for (const name of ['white', 'black', 'beige', 'navy', 'blue', 'gray', 'orange', 'yellow']) {
      expect(colorSwatch(name)).toMatch(/^#[0-9a-f]{6}$/);
    }
  });

  it('maps Vietnamese names to the same color as their English counterpart', () => {
    expect(colorSwatch('trắng')).toBe(colorSwatch('white'));
    expect(colorSwatch('đen')).toBe(colorSwatch('black'));
    expect(colorSwatch('be')).toBe(colorSwatch('beige'));
    expect(colorSwatch('xám')).toBe(colorSwatch('gray'));
    expect(colorSwatch('nâu')).toBe(colorSwatch('brown'));
    expect(colorSwatch('cam')).toBe(colorSwatch('orange'));
    expect(colorSwatch('vàng')).toBe(colorSwatch('yellow'));
    expect(colorSwatch('đỏ')).toBe(colorSwatch('red'));
    expect(colorSwatch('hồng')).toBe(colorSwatch('pink'));
    expect(colorSwatch('xanh')).toBe(colorSwatch('blue'));
  });

  it('is case and whitespace insensitive', () => {
    expect(colorSwatch('  White ')).toBe(colorSwatch('white'));
    expect(colorSwatch('ĐEN')).toBe(colorSwatch('đen'));
    expect(colorSwatch('Xanh   Lá')).toBe(colorSwatch('green'));
  });

  it('prefers the most specific name ("xanh lá" is green, not blue)', () => {
    expect(colorSwatch('xanh lá')).toBe(colorSwatch('green'));
    expect(colorSwatch('xanh lá')).not.toBe(colorSwatch('blue'));
    expect(colorSwatch('xanh navy')).toBe(colorSwatch('navy'));
  });

  it('finds a known color inside a longer name', () => {
    expect(colorSwatch('đen nhám')).toBe(colorSwatch('black'));
    expect(colorSwatch('light pink')).toBe(colorSwatch('pink'));
  });

  it('returns null for unknown or empty names', () => {
    expect(colorSwatch('galaxy')).toBeNull();
    expect(colorSwatch('')).toBeNull();
    expect(colorSwatch(undefined)).toBeNull();
    expect(colorSwatch(null)).toBeNull();
  });
});
