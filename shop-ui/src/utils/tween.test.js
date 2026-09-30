import { describe, expect, it } from 'vitest';
import { easeOutCubic, tweenValue } from './tween.js';

describe('easeOutCubic', () => {
  it('runs from 0 to 1 and clamps', () => {
    expect(easeOutCubic(0)).toBe(0);
    expect(easeOutCubic(1)).toBe(1);
    expect(easeOutCubic(-1)).toBe(0);
    expect(easeOutCubic(2)).toBe(1);
    expect(easeOutCubic(0.5)).toBeGreaterThan(0.5);
  });
});

describe('tweenValue', () => {
  it('starts at from and lands exactly on to', () => {
    expect(tweenValue(100, 200, 0)).toBe(100);
    expect(tweenValue(100, 200, 1)).toBe(200);
  });
  it('works downwards and returns integers', () => {
    const mid = tweenValue(500000, 300000, 0.4);
    expect(Number.isInteger(mid)).toBe(true);
    expect(mid).toBeLessThan(500000);
    expect(mid).toBeGreaterThan(300000);
  });
});
