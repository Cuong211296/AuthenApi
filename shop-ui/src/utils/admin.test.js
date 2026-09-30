import { describe, expect, it } from 'vitest';
import { countBy, countError, editorSnapshot, filterProducts, isEditorDirty, filterRates, mergeVariants, optionalCountError, overrideOf, parseCount, slugify, variantErrors } from './admin.js';

describe('filterProducts', () => {
  const items = [
    { name: 'Áo thun cotton', slug: 'ao-thun-cotton', categoryName: 'Áo thun' },
    { name: 'Quần jeans', slug: 'quan-jeans', categoryName: 'Quần' },
    { name: 'Không danh mục', slug: 'khong', categoryName: null },
  ];
  it('returns everything for an empty query', () => expect(filterProducts(items, '  ')).toHaveLength(3));
  it('ignores diacritics and case', () => expect(filterProducts(items, 'AO').map((p) => p.slug)).toEqual(['ao-thun-cotton']));
  it('matches on category and requires every word', () => {
    expect(filterProducts(items, 'quan jeans')).toHaveLength(1);
    expect(filterProducts(items, 'quan cotton')).toHaveLength(0);
  });
  it('handles missing category', () => expect(filterProducts(items, 'khong')).toHaveLength(1));
});

describe('filterRates', () => {
  it('matches provinces without diacritics', () => {
    const rates = [{ province: 'Hà Nội' }, { province: 'Đà Nẵng' }];
    expect(filterRates(rates, 'da nang')).toEqual([{ province: 'Đà Nẵng' }]);
  });
});

describe('parseCount / countError', () => {
  it('parses whole non-negative numbers', () => {
    expect(parseCount('35000')).toBe(35000);
    expect(parseCount(' 0 ')).toBe(0);
    expect(parseCount(12)).toBe(12);
  });
  it('rejects empty, negative, decimal and junk', () => {
    for (const v of ['', ' ', '-1', '1.5', '1e3', 'abc', null, undefined, -2, 1.5]) expect(parseCount(v)).toBeNull();
  });
  it('requires a value instead of silently using 0', () => {
    expect(countError('', 'Phí')).toMatch(/bắt buộc/);
    expect(countError('-3', 'Phí')).toMatch(/số nguyên không âm/);
    expect(countError('0', 'Phí')).toBe('');
  });
  it('optionalCountError allows empty only', () => {
    expect(optionalCountError('', 'Giá')).toBe('');
    expect(optionalCountError('x', 'Giá')).not.toBe('');
  });
});

describe('variantErrors', () => {
  it('flags an empty stock and a bad override', () => {
    expect(variantErrors({ stock: '', priceOverride: '' })).toEqual({ stock: 'Tồn kho là bắt buộc' });
    expect(Object.keys(variantErrors({ stock: '3', priceOverride: '-1' }))).toEqual(['price']);
    expect(variantErrors({ stock: '0', priceOverride: '' })).toEqual({});
  });
});

describe('overrideOf / mergeVariants', () => {
  it('shows an override only when the price differs from the base', () => {
    expect(overrideOf({ price: 100 }, 100)).toBe('');
    expect(overrideOf({ price: 120 }, 100)).toBe('120');
  });
  it('keeps unsaved local edits, refreshes the saved id and appends new variants', () => {
    const local = [
      { id: 'a', stock: '99', priceOverride: '' },
      { id: 'b', stock: '7', priceOverride: '' },
    ];
    const server = [
      { id: 'a', stock: 5, price: 100 },
      { id: 'b', stock: 8, price: 150 },
      { id: 'c', stock: 1, price: 100 },
    ];
    const merged = mergeVariants(local, server, 100, ['b']);
    expect(merged[0]).toBe(local[0]);
    expect(merged[1]).toMatchObject({ id: 'b', stock: 8, priceOverride: '150' });
    expect(merged[2]).toMatchObject({ id: 'c', priceOverride: '' });
  });
});

describe('countBy', () => {
  it('counts by key', () => expect(countBy([{ s: 'x' }, { s: 'y' }, { s: 'x' }], (i) => i.s)).toEqual({ x: 2, y: 1 }));
});

describe('slugify', () => {
  it('builds a dashed ascii slug', () => {
    expect(slugify('Áo thun  Đen (Basic)!')).toBe('ao-thun-den-basic');
    expect(slugify('  ')).toBe('');
  });
});

describe('isEditorDirty', () => {
  const editing = () => ({
    name: 'A', slug: 'a', description: null, categoryId: 'c1', basePrice: 100, imageUrl: '', active: true,
    variants: [{ id: 'v1', stock: 5, priceOverride: '', active: true }],
  });
  it('is clean right after the snapshot', () => {
    const e = editing();
    expect(isEditorDirty(editorSnapshot(e), e)).toBe(false);
  });
  it('treats null/undefined/number vs string as equal', () => {
    const base = editorSnapshot(editing());
    expect(isEditorDirty(base, { ...editing(), description: '', basePrice: '100', variants: [{ id: 'v1', stock: '5', priceOverride: '', active: true }] })).toBe(false);
  });
  it('detects product field edits', () => {
    const base = editorSnapshot(editing());
    expect(isEditorDirty(base, { ...editing(), name: 'B' })).toBe(true);
    expect(isEditorDirty(base, { ...editing(), active: false })).toBe(true);
    expect(isEditorDirty(base, { ...editing(), categoryId: '' })).toBe(true);
  });
  it('reads the category from category.id when categoryId is unset', () => {
    const e = { ...editing(), categoryId: undefined, category: { id: 'c1' } };
    expect(isEditorDirty(editorSnapshot(e), e)).toBe(false);
  });
  it('detects variant edits but ignores variants added after the baseline', () => {
    const base = editorSnapshot(editing());
    const changed = editing(); changed.variants[0].stock = '9';
    expect(isEditorDirty(base, changed)).toBe(true);
    const price = editing(); price.variants[0].priceOverride = '10';
    expect(isEditorDirty(base, price)).toBe(true);
    const added = editing(); added.variants.push({ id: 'v2', stock: 1, priceOverride: '', active: true });
    expect(isEditorDirty(base, added)).toBe(false);
  });
  it('is false without a baseline', () => expect(isEditorDirty(null, editing())).toBe(false));
});
