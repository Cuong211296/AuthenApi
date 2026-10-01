import { describe, expect, it } from 'vitest';
import {
  bandScale, bucketKind, delta, estimateTextWidth, extent, formatBucket, formatBucketLong, formatCount, formatPercent,
  formatSignedPercent, formatVndCompact, formatVndFull, linearScale, maxLabelsFor, nearestIndexByX, niceStep, niceTicks,
  pathFromPoints, thinIndices,
} from './scales.js';

// Intl may use a narrow no-break space in some locales; normalize for stable assertions.
const norm = (s) => s.replace(/[  ]/g, ' ');

describe('niceStep', () => {
  it('picks 1/2/2.5/5 x 10^k', () => {
    expect(niceStep(0.7)).toBe(1);
    expect(niceStep(1.3)).toBe(2);
    expect(niceStep(2.2)).toBe(2.5);
    expect(niceStep(3)).toBe(5);
    expect(niceStep(7)).toBe(10);
    expect(niceStep(2_400_000)).toBe(2_500_000);
  });
  it('falls back to 1 for zero, negative and non-finite input', () => {
    expect(niceStep(0)).toBe(1);
    expect(niceStep(-5)).toBe(1);
    expect(niceStep(NaN)).toBe(1);
  });
});

describe('niceTicks', () => {
  it('covers the domain with round values', () => {
    expect(niceTicks(0, 12_000_000, 4)).toEqual([0, 5_000_000, 10_000_000, 15_000_000]);
    expect(niceTicks(0, 100, 5)).toEqual([0, 20, 40, 60, 80, 100]);
  });
  it('first tick <= min and last tick >= max', () => {
    const t = niceTicks(3, 97, 5);
    expect(t[0]).toBeLessThanOrEqual(3);
    expect(t[t.length - 1]).toBeGreaterThanOrEqual(97);
  });
  it('widens a zero domain to [0, 1]', () => {
    const t = niceTicks(0, 0, 4);
    expect(t[0]).toBe(0);
    expect(t[t.length - 1]).toBe(1);
  });
  it('includes zero for a single positive or negative value', () => {
    expect(niceTicks(500, 500, 5)[0]).toBe(0);
    const neg = niceTicks(-40, -40, 4);
    expect(neg[0]).toBeLessThanOrEqual(-40);
    expect(neg[neg.length - 1]).toBe(0);
  });
  it('handles tiny ranges without float noise', () => {
    const t = niceTicks(0, 0.003, 3);
    expect(t).toEqual([0, 0.001, 0.002, 0.003]);
    expect(niceTicks(0, 0.3, 3)).toEqual([0, 0.1, 0.2, 0.3]);
  });
  it('handles negative ranges and swapped bounds', () => {
    expect(niceTicks(-50, 50, 4)).toEqual([-50, -25, 0, 25, 50]);
    expect(niceTicks(10, 0, 5)).toEqual(niceTicks(0, 10, 5));
  });
  it('handles huge values', () => {
    const t = niceTicks(0, 3.4e12, 4);
    expect(t[0]).toBe(0);
    expect(t[t.length - 1]).toBeGreaterThanOrEqual(3.4e12);
    expect(t.length).toBeLessThanOrEqual(6);
  });
  it('integer mode never steps below 1 (counts)', () => {
    expect(niceTicks(0, 3, 5, { integer: true })).toEqual([0, 1, 2, 3]);
    expect(niceTicks(0, 0, 4, { integer: true })).toEqual([0, 1]);
    expect(niceTicks(0, 9, 4, { integer: true }).every(Number.isInteger)).toBe(true);
  });
  it('treats non-finite bounds as 0', () => {
    expect(niceTicks(NaN, undefined, 4)).toEqual(niceTicks(0, 0, 4));
  });
});

describe('linearScale', () => {
  it('maps and inverts', () => {
    const y = linearScale([0, 100], [200, 0]);
    expect(y(0)).toBe(200);
    expect(y(50)).toBe(100);
    expect(y.invert(50)).toBe(75);
  });
  it('maps a zero-width domain to the range midpoint', () => {
    expect(linearScale([5, 5], [0, 100])(5)).toBe(50);
  });
});

describe('bandScale', () => {
  it('divides the range with a 2px gap', () => {
    const b = bandScale(10, [0, 100]);
    expect(b.step).toBe(10);
    expect(b.bandwidth).toBe(8);
    expect(b.start(0)).toBe(1);
    expect(b.center(9)).toBe(95);
  });
  it('never returns a band below 1px for dense data (92 bars in 150px)', () => {
    const b = bandScale(92, [0, 150]);
    expect(b.bandwidth).toBe(1);
    expect(b.center(91)).toBeLessThanOrEqual(150);
  });
  it('handles zero items', () => {
    expect(bandScale(0, [0, 100]).bandwidth).toBe(0);
  });
});

describe('pathFromPoints', () => {
  const pts = [{ x: 0, y: 10 }, { x: 10, y: 0 }, { x: 20, y: 10 }];
  it('returns empty strings for no data', () => {
    expect(pathFromPoints([])).toEqual({ line: '', area: '' });
    expect(pathFromPoints(undefined)).toEqual({ line: '', area: '' });
  });
  it('draws a straight line and closes the area to the baseline', () => {
    const { line, area } = pathFromPoints(pts, { baseline: 50 });
    expect(line).toBe('M0,10L10,0L20,10');
    expect(area).toBe('M0,10L10,0L20,10L20,50L0,50Z');
  });
  it('a single point is a moveto with no area', () => {
    expect(pathFromPoints([{ x: 5, y: 5 }])).toEqual({ line: 'M5,5', area: '' });
  });
  it('null y values split the line into runs', () => {
    const { line, area } = pathFromPoints([{ x: 0, y: 1 }, { x: 1, y: 2 }, { x: 2, y: null }, { x: 3, y: 4 }, { x: 4, y: 5 }], { baseline: 9 });
    expect(line).toBe('M0,1L1,2M3,4L4,5');
    expect(area.match(/Z/g)).toHaveLength(2);
  });
  it('monotone curves pass through every point and do not overshoot', () => {
    const data = [{ x: 0, y: 100 }, { x: 10, y: 100 }, { x: 20, y: 0 }, { x: 30, y: 100 }, { x: 40, y: 100 }];
    const { line } = pathFromPoints(data, { curve: 'monotone' });
    expect(line.startsWith('M0,100C')).toBe(true);
    const nums = line.replace(/[MC]/g, ',').split(',').filter(Boolean).map(Number);
    const ys = nums.filter((_, i) => i % 2 === 1);
    expect(Math.min(...ys)).toBeGreaterThanOrEqual(0);
    expect(Math.max(...ys)).toBeLessThanOrEqual(100);
    for (const p of data) expect(line).toContain(`${p.x},${p.y}`);
  });
  it('monotone with two points is a straight segment', () => {
    expect(pathFromPoints([{ x: 0, y: 0 }, { x: 4, y: 4 }], { curve: 'monotone' }).line).toBe('M0,0L4,4');
  });
});

describe('nearestIndexByX', () => {
  const xs = [0, 10, 20, 30];
  it('finds the closest index', () => {
    expect(nearestIndexByX(xs, 14)).toBe(1);
    expect(nearestIndexByX(xs, 16)).toBe(2);
    expect(nearestIndexByX(xs, 15)).toBe(1);
  });
  it('clamps outside the range', () => {
    expect(nearestIndexByX(xs, -50)).toBe(0);
    expect(nearestIndexByX(xs, 999)).toBe(3);
  });
  it('handles empty and single lists', () => {
    expect(nearestIndexByX([], 3)).toBe(-1);
    expect(nearestIndexByX([7], 100)).toBe(0);
  });
});

describe('label thinning', () => {
  it('keeps every label when they fit', () => {
    expect(thinIndices(5, 6)).toEqual([0, 1, 2, 3, 4]);
  });
  it('steps back from the last label', () => {
    expect(thinIndices(30, 6)).toEqual([4, 9, 14, 19, 24, 29]);
    expect(thinIndices(92, 6).length).toBeLessThanOrEqual(6);
    expect(thinIndices(92, 6).at(-1)).toBe(91);
  });
  it('handles empty, one and a zero cap', () => {
    expect(thinIndices(0, 6)).toEqual([]);
    expect(thinIndices(1, 6)).toEqual([0]);
    expect(thinIndices(10, 0)).toEqual([9]);
  });
  it('maxLabelsFor fits labels with a gap and caps the count', () => {
    expect(maxLabelsFor(300, 34)).toBe(6);
    expect(maxLabelsFor(2000, 34)).toBe(12);
    expect(maxLabelsFor(0, 34)).toBe(1);
    expect(maxLabelsFor(20, 34)).toBe(1);
  });
  it('estimates text width from length', () => {
    expect(estimateTextWidth('05/09', 12)).toBeCloseTo(36);
    expect(estimateTextWidth(null)).toBe(0);
  });
});

describe('formatVndCompact', () => {
  it('uses N / tr / tỷ with a decimal comma', () => {
    expect(norm(formatVndCompact(0))).toBe('0');
    expect(norm(formatVndCompact(999))).toBe('999');
    expect(norm(formatVndCompact(450_000))).toBe('450 N');
    expect(norm(formatVndCompact(1_200_000))).toBe('1,2 tr');
    expect(norm(formatVndCompact(1_250_000))).toBe('1,25 tr');
    expect(norm(formatVndCompact(12_000_000))).toBe('12 tr');
    expect(norm(formatVndCompact(1_200_000_000))).toBe('1,2 tỷ');
  });
  it('promotes values that round up to 1000 of a unit', () => {
    expect(norm(formatVndCompact(999_999))).toBe('1 tr');
    expect(norm(formatVndCompact(999_999_999))).toBe('1 tỷ');
    expect(norm(formatVndCompact(999.7))).toBe('1 N');
  });
  it('handles negatives, huge values and bad input', () => {
    expect(norm(formatVndCompact(-1_500_000))).toBe('-1,5 tr');
    expect(norm(formatVndCompact(3_400_000_000_000))).toBe('3.400 tỷ');
    expect(formatVndCompact(null)).toBe('0');
    expect(formatVndCompact(NaN)).toBe('0');
  });
});

describe('full formats', () => {
  it('formats VND fully and counts with grouping', () => {
    expect(norm(formatVndFull(1_200_000))).toBe('1.200.000 ₫');
    expect(formatVndFull(null)).toBe('—');
    expect(formatCount(12345)).toBe('12.345');
    expect(formatCount(undefined)).toBe('—');
  });
  it('formats percents', () => {
    expect(formatPercent(0.125)).toBe('12,5%');
    expect(formatPercent(0)).toBe('0%');
    expect(formatPercent(1)).toBe('100%');
    expect(formatPercent(0.33333, 0)).toBe('33%');
    expect(formatPercent(null)).toBe('—');
  });
  it('formats signed percents with a true minus', () => {
    expect(formatSignedPercent(0.125)).toBe('+12,5%');
    expect(formatSignedPercent(-0.03)).toBe('−3%');
    expect(formatSignedPercent(0)).toBe('0%');
    expect(formatSignedPercent(0.00001)).toBe('0%');
    expect(formatSignedPercent(null)).toBe('—');
  });
});

describe('bucket labels', () => {
  it('detects the bucket kind', () => {
    expect(bucketKind('2026-09-05')).toBe('day');
    expect(bucketKind('2026-09')).toBe('month');
    expect(bucketKind('2026')).toBe('year');
    expect(bucketKind('abc')).toBeNull();
    expect(bucketKind(null)).toBeNull();
  });
  it('short labels per groupBy', () => {
    expect(formatBucket('2026-09-05', 'day')).toBe('05/09');
    expect(formatBucket('2026-09', 'month')).toBe('09/2026');
    expect(formatBucket('2026', 'year')).toBe('2026');
    expect(formatBucket('2026-09-05')).toBe('05/09');
    expect(formatBucket('xx', 'day')).toBe('xx');
  });
  it('long labels with weekday for days', () => {
    expect(formatBucketLong('2026-09-05')).toBe('T7, 05/09/2026');
    expect(formatBucketLong('2026-09-06')).toBe('CN, 06/09/2026');
    expect(formatBucketLong('2026-09')).toBe('Tháng 09/2026');
    expect(formatBucketLong('2026')).toBe('Năm 2026');
    expect(formatBucketLong(undefined)).toBe('');
  });
});

describe('delta', () => {
  it('computes abs, pct and direction', () => {
    expect(delta(150, 100)).toEqual({ abs: 50, pct: 0.5, direction: 'up' });
    expect(delta(50, 100)).toEqual({ abs: -50, pct: -0.5, direction: 'down' });
    expect(delta(7, 7)).toEqual({ abs: 0, pct: 0, direction: 'flat' });
  });
  it('pct is null when the previous value is 0', () => {
    expect(delta(10, 0)).toEqual({ abs: 10, pct: null, direction: 'up' });
    expect(delta(0, 0)).toEqual({ abs: 0, pct: null, direction: 'flat' });
  });
  it('uses |previous| for negative previous values (profit)', () => {
    expect(delta(-50, -100)).toEqual({ abs: 50, pct: 0.5, direction: 'up' });
  });
  it('treats float noise as flat and missing values as null', () => {
    expect(delta(0.1 + 0.2, 0.3).direction).toBe('flat');
    expect(delta(null, 5)).toBeNull();
    expect(delta(5, undefined)).toBeNull();
  });
});

describe('extent', () => {
  it('ignores non-finite values', () => {
    expect(extent([3, null, -2, NaN, 9])).toEqual({ min: -2, max: 9 });
    expect(extent([])).toEqual({ min: 0, max: 0 });
    expect(extent(undefined)).toEqual({ min: 0, max: 0 });
  });
});
