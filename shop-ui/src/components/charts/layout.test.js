import { describe, expect, it } from 'vitest';
import { barPath, barShare, cartesianLayout, partShares, clampLabelX, lastFiniteIndex, maxIndex, MAX_BAR } from './layout.js';
import { formatBucket, formatVndCompact } from './scales.js';

const days = (n) => Array.from({ length: n }, (_, i) => formatBucket(`2026-09-${String((i % 30) + 1).padStart(2, '0')}`));

describe('cartesianLayout', () => {
  it('spreads points edge to edge and keeps the zero baseline at the bottom for positive data', () => {
    const l = cartesianLayout({ width: 800, height: 260, values: [0, 5e6, 12e6], xLabels: days(3), formatY: formatVndCompact });
    expect(l.xs[0]).toBe(l.left);
    expect(l.xs[2]).toBeCloseTo(l.left + l.plotW);
    expect(l.baselineY).toBeCloseTo(l.top + l.plotH);
    expect(l.ticks[l.ticks.length - 1]).toBeGreaterThanOrEqual(12e6);
  });
  it('reserves right room for an end label', () => {
    const plain = cartesianLayout({ width: 800, height: 260, values: [1, 2], xLabels: days(2) });
    const labelled = cartesianLayout({ width: 800, height: 260, values: [1, 2], xLabels: days(2), endLabelText: '7,65 tr' });
    expect(labelled.right).toBeGreaterThan(plain.right + 30);
    expect(labelled.plotW).toBeLessThan(plain.plotW);
  });
  it('centers a single point', () => {
    const l = cartesianLayout({ width: 400, height: 220, values: [7], xLabels: ['05/09'] });
    expect(l.xs[0]).toBeCloseTo(l.left + l.plotW / 2);
    expect(l.labelIdx).toEqual([0]);
  });
  it('hides non-zero tick labels when every value is zero', () => {
    const l = cartesianLayout({ width: 400, height: 220, values: [0, 0, 0], xLabels: days(3) });
    expect(l.allZero).toBe(true);
    expect(l.tickLabels.filter(Boolean)).toEqual(['0']);
  });
  it('handles empty data', () => {
    const l = cartesianLayout({ width: 400, height: 220, values: [], xLabels: [] });
    expect(l.xs).toEqual([]);
    expect(l.labelIdx).toEqual([]);
  });
  it('puts the baseline inside the plot for negative values', () => {
    const l = cartesianLayout({ width: 400, height: 260, values: [-200, 400], xLabels: days(2) });
    expect(l.baselineY).toBeGreaterThan(l.top);
    expect(l.baselineY).toBeLessThan(l.top + l.plotH);
  });
  it('caps bars at 24px and thins labels for 92 bars on mobile', () => {
    const wide = cartesianLayout({ width: 1200, height: 260, values: [1, 2], xLabels: days(2), band: true });
    expect(wide.bandwidth).toBe(MAX_BAR);
    const dense = cartesianLayout({ width: 358, height: 240, values: Array(92).fill(3), xLabels: days(92), band: true, integer: true });
    expect(dense.bandwidth).toBeGreaterThanOrEqual(1);
    expect(dense.bandwidth).toBeLessThan(4);
    expect(dense.labelIdx.length).toBeLessThanOrEqual(6);
    expect(dense.labelIdx.at(-1)).toBe(91);
    expect(dense.ticks.every(Number.isInteger)).toBe(true);
  });
  it('never shows more than 12 labels on desktop', () => {
    const l = cartesianLayout({ width: 2400, height: 260, values: [1], xLabels: days(90) });
    expect(l.labelIdx.length).toBeLessThanOrEqual(12);
  });
});

describe('clampLabelX', () => {
  it('keeps a label inside the chart', () => {
    expect(clampLabelX(2, 40, 300)).toBe(20);
    expect(clampLabelX(299, 40, 300)).toBe(280);
    expect(clampLabelX(150, 40, 300)).toBe(150);
    expect(clampLabelX(5, 400, 300)).toBe(150);
  });
});

describe('barPath', () => {
  it('draws an upward bar with a rounded top', () => {
    const d = barPath(50, 20, 200, 100);
    expect(d.startsWith('M40,200V104A4,4')).toBe(true);
    expect(d.endsWith('V200Z')).toBe(true);
  });
  it('draws a downward bar for negative values', () => {
    expect(barPath(50, 20, 100, 200).startsWith('M40,100V196A4,4')).toBe(true);
  });
  it('shrinks the radius for thin or short bars and skips empty bars', () => {
    expect(barPath(10, 2, 100, 50)).toContain('A1,1');
    expect(barPath(10, 20, 100, 98)).toContain('A2,2');
    expect(barPath(10, 20, 100, 100)).toBe('');
    expect(barPath(10, 0, 100, 50)).toBe('');
  });
});

describe('index helpers', () => {
  it('finds the last finite value', () => {
    expect(lastFiniteIndex([1, 2, null])).toBe(1);
    expect(lastFiniteIndex([])).toBe(-1);
    expect(lastFiniteIndex(undefined)).toBe(-1);
  });
  it('finds the largest positive value', () => {
    expect(maxIndex([3, 9, 9, 1])).toBe(1);
    expect(maxIndex([0, 0])).toBe(-1);
    expect(maxIndex([-3, null])).toBe(-1);
  });
});

describe('barShare', () => {
  it('scales against the max and is safe for zero, negative and missing values', () => {
    expect(barShare(5, 10)).toBe(0.5);
    expect(barShare(10, 10)).toBe(1);
    expect(barShare(0, 10)).toBe(0);
    expect(barShare(-3, 10)).toBe(0);
    expect(barShare(null, 10)).toBe(0);
    expect(barShare(5, 0)).toBe(0);
    expect(barShare(2e12, 4e12)).toBe(0.5);
  });
});

describe('partShares', () => {
  it('returns the total and shares', () => {
    expect(partShares([3, 1])).toEqual({ total: 4, shares: [0.75, 0.25] });
  });
  it('treats negative and missing parts as 0 and handles an all-zero or empty whole', () => {
    expect(partShares([5, -2, null])).toEqual({ total: 5, shares: [1, 0, 0] });
    expect(partShares([0, 0])).toEqual({ total: 0, shares: [0, 0] });
    expect(partShares(undefined)).toEqual({ total: 0, shares: [] });
  });
});
