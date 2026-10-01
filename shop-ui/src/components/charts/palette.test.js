import { describe, expect, it } from 'vitest';
import { CATEGORICAL, OTHER, SEMANTIC, SEQUENTIAL, contrastRatio, deltaTone, inkOn, luminance, seriesColor } from './palette.js';

describe('palette', () => {
  it('has eight distinct categorical slots, accent first', () => {
    expect(CATEGORICAL).toHaveLength(8);
    expect(new Set(CATEGORICAL).size).toBe(8);
    expect(CATEGORICAL[0]).toBe('#c8501e');
  });
  it('keeps semantic colors out of the categorical and sequential sets', () => {
    for (const c of Object.values(SEMANTIC)) {
      expect(CATEGORICAL).not.toContain(c);
      expect(SEQUENTIAL).not.toContain(c);
    }
  });
  it('the two slots the admin uses clear 3:1 on the white card', () => {
    expect(contrastRatio(CATEGORICAL[0], '#ffffff')).toBeGreaterThanOrEqual(3);
    expect(contrastRatio(CATEGORICAL[1], '#ffffff')).toBeGreaterThanOrEqual(3);
  });
  it('semantic colors are readable as text (>= 4.5:1 on white)', () => {
    for (const c of Object.values(SEMANTIC)) expect(contrastRatio(c, '#ffffff')).toBeGreaterThanOrEqual(4.5);
  });
  it('the sequential ramp runs light to dark', () => {
    const l = SEQUENTIAL.map(luminance);
    for (let i = 1; i < l.length; i += 1) expect(l[i]).toBeLessThan(l[i - 1]);
  });
});

describe('seriesColor', () => {
  it('assigns slots in order and never cycles', () => {
    expect(seriesColor(0)).toBe(CATEGORICAL[0]);
    expect(seriesColor(7)).toBe(CATEGORICAL[7]);
    expect(seriesColor(8)).toBe(OTHER);
    expect(seriesColor(-1)).toBe(OTHER);
    expect(seriesColor(1.5)).toBe(OTHER);
  });
});

describe('deltaTone', () => {
  it('up is good by default', () => {
    expect(deltaTone('up')).toBe('good');
    expect(deltaTone('down')).toBe('bad');
    expect(deltaTone('flat')).toBe('neutral');
  });
  it('inverts when growth is bad (cancel rate)', () => {
    expect(deltaTone('up', false)).toBe('bad');
    expect(deltaTone('down', false)).toBe('good');
    expect(deltaTone(undefined, false)).toBe('neutral');
  });
});

describe('contrast helpers', () => {
  it('computes WCAG contrast', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 0);
    expect(contrastRatio('#ffffff', '#ffffff')).toBe(1);
    expect(luminance('nope')).toBe(0);
  });
  it('picks white on dark fills and ink on light fills', () => {
    expect(inkOn('#4a3aa7')).toBe('#ffffff');
    expect(inkOn('#eda100')).toBe('#14110f');
  });
});
