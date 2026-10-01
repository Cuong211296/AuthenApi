import {
  cartKeyOf, describeQuote, formatWeight, orderTotal, quoteErrorMessage, quoteInputsReady, shippingDisplay, shippingSourceLabel,
} from './shipping.js';

const ghtk = { fee: 32000, source: 'GHTK', estimated: false, weightGrams: 600, deliverable: true, message: null };
const table = { fee: 35000, source: 'TABLE', estimated: true, weightGrams: 600, deliverable: true, message: 'GHTK tạm thời không khả dụng' };

describe('quoteInputsReady', () => {
  it('needs a province and a ward (trimmed)', () => {
    expect(quoteInputsReady({ province: 'Hà Nội', ward: 'Phường 1' })).toBe(true);
    expect(quoteInputsReady({ province: 'Hà Nội', ward: '   ' })).toBe(false);
    expect(quoteInputsReady({ province: '', ward: 'Phường 1' })).toBe(false);
    expect(quoteInputsReady({})).toBe(false);
  });
});

describe('describeQuote', () => {
  it('labels an exact GHTK fee', () => {
    expect(describeQuote(ghtk)).toEqual({ label: 'Phí GHTK', tone: 'accent', note: '' });
  });
  it('labels table and estimated fees as provisional and keeps the server message', () => {
    expect(describeQuote(table)).toEqual({ label: 'Phí tạm tính', tone: 'warn', note: 'GHTK tạm thời không khả dụng' });
    expect(describeQuote({ ...ghtk, estimated: true }).label).toBe('Phí tạm tính');
  });
});

describe('shippingSourceLabel', () => {
  it('maps stored sources and hides unknown ones', () => {
    expect(shippingSourceLabel('GHTK').label).toBe('Phí GHTK');
    expect(shippingSourceLabel('TABLE').label).toBe('Phí tạm tính');
    expect(shippingSourceLabel(null)).toBeNull();
    expect(shippingSourceLabel(undefined)).toBeNull();
  });
});

describe('formatWeight', () => {
  it('formats grams as kg with a decimal comma', () => {
    expect(formatWeight(600)).toBe('0,6 kg');
    expect(formatWeight(1250)).toBe('1,25 kg');
    expect(formatWeight(null)).toBe('');
    expect(formatWeight(0)).toBe('');
  });
});

describe('shippingDisplay', () => {
  const province = 'Hà Nội';
  it('shows nothing until a province is chosen', () => {
    expect(shippingDisplay({ state: 'idle', quote: null, tableFee: 30000, province: '' })).toBeNull();
  });
  it('uses the quote when ready', () => {
    expect(shippingDisplay({ state: 'ready', quote: ghtk, tableFee: 30000, province })).toMatchObject({ fee: 32000, label: 'Phí GHTK', provisional: false, busy: false });
  });
  it('keeps the previous quote (busy) while loading, else the table fee', () => {
    expect(shippingDisplay({ state: 'loading', quote: ghtk, tableFee: 30000, province })).toMatchObject({ fee: 32000, busy: true });
    expect(shippingDisplay({ state: 'loading', quote: null, tableFee: 30000, province })).toMatchObject({ fee: 30000, label: 'Phí tạm tính', provisional: true, busy: true });
  });
  it('falls back to the table fee when idle or failed, with a note on failure', () => {
    expect(shippingDisplay({ state: 'idle', quote: null, tableFee: 30000, province })).toMatchObject({ fee: 30000, provisional: true, note: expect.stringMatching(/phường/) });
    const failed = shippingDisplay({ state: 'error', quote: null, tableFee: 30000, province });
    expect(failed).toMatchObject({ fee: 30000, label: 'Phí tạm tính' });
    expect(failed.note).toMatch(/sẽ chốt khi đặt hàng/);
  });
  it('treats an unknown table fee as 0', () => {
    expect(shippingDisplay({ state: 'idle', quote: null, tableFee: undefined, province }).fee).toBe(0);
  });
});

describe('quoteErrorMessage', () => {
  it('maps known codes and falls back to the generic note', () => {
    expect(quoteErrorMessage({ code: 2009 })).toMatch(/chưa được hỗ trợ/);
    expect(quoteErrorMessage({ code: 2008 })).toMatch(/trống/);
    expect(quoteErrorMessage({ code: -1 })).toMatch(/sẽ chốt khi đặt hàng/);
    expect(quoteErrorMessage(null)).toMatch(/sẽ chốt khi đặt hàng/);
  });
});

describe('orderTotal / cartKeyOf', () => {
  it('adds the fee to the subtotal', () => {
    expect(orderTotal(500000, 32000)).toBe(532000);
    expect(orderTotal(500000, undefined)).toBe(500000);
  });
  it('changes when quantity or subtotal changes', () => {
    const a = cartKeyOf({ totalQuantity: 2, subtotal: 100 });
    expect(cartKeyOf({ totalQuantity: 3, subtotal: 100 })).not.toBe(a);
    expect(cartKeyOf({ totalQuantity: 2, subtotal: 150 })).not.toBe(a);
    expect(cartKeyOf({ totalQuantity: 2, subtotal: 100 })).toBe(a);
  });
});
