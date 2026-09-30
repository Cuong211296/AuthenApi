import { describe, expect, it } from 'vitest';
import { initialsOf } from './initials.js';

describe('initialsOf', () => {
  it('takes the first letters of up to two words', () => {
    expect(initialsOf('Áo thun trơn')).toBe('ÁT');
    expect(initialsOf('jeans')).toBe('J');
  });
  it('handles empty input', () => {
    expect(initialsOf('')).toBe('');
    expect(initialsOf('   ')).toBe('');
    expect(initialsOf(undefined)).toBe('');
  });
});
