import {
  applyCarrier, carrierForOrder, cartKeyOf, chosenOption, describeQuote, hasCarrierChoice, quoteOptions, formatWeight, orderTotal, quoteErrorMessage, quoteInputsReady, shippingDisplay, shippingHint, shippingSourceLabel,
} from './shipping.js';

const ghtk = { fee: 32000, source: 'GHTK', estimated: false, weightGrams: 600, deliverable: true, message: null };
const ghn = { fee: 28000, source: 'GHN', estimated: false, weightGrams: 600, deliverable: true, message: null };
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

describe('GHN labels', () => {
  it('labels an exact GHN fee like GHTK (accent) and an estimated one as provisional', () => {
    expect(describeQuote(ghn)).toEqual({ label: 'Phí GHN', tone: 'accent', note: '' });
    expect(describeQuote({ ...ghn, estimated: true }).label).toBe('Phí tạm tính');
  });
  it('maps the stored GHN source', () => {
    expect(shippingSourceLabel('GHN')).toEqual({ label: 'Phí GHN', tone: 'accent' });
  });
});

describe('shippingDisplay in GHN_IDS mode', () => {
  const mode = 'GHN_IDS';
  it('shows the hint (null) until the address is complete, even with a table fee at hand', () => {
    expect(shippingDisplay({ state: 'idle', quote: null, tableFee: 30000, province: 'Hà Nội', mode })).toBeNull();
    expect(shippingHint('GHN_IDS')).toBe('Chọn đầy đủ địa chỉ để tính phí');
    expect(shippingHint('TEXT')).toBe('Chọn tỉnh/thành');
  });
  it('shows the GHN fee when ready and dims the previous one while reloading', () => {
    expect(shippingDisplay({ state: 'ready', quote: ghn, mode })).toMatchObject({ fee: 28000, label: 'Phí GHN', busy: false, provisional: false });
    expect(shippingDisplay({ state: 'loading', quote: ghn, mode })).toMatchObject({ fee: 28000, label: 'Phí GHN', busy: true });
  });
  it('is pending (no table fee) on the first load', () => {
    expect(shippingDisplay({ state: 'loading', quote: null, tableFee: 30000, mode })).toMatchObject({ pending: true, busy: true, fee: 0 });
  });
  it('a failed quote has an unknown fee and the non-blocking note', () => {
    const failed = shippingDisplay({ state: 'error', quote: null, tableFee: 30000, mode });
    expect(failed).toMatchObject({ unknownFee: true, fee: 0, label: 'Phí tạm tính' });
    expect(failed.note).toMatch(/sẽ chốt khi đặt hàng/);
  });
  it('keeps the quote visible when deliverable is false (the checkout blocks on the flag)', () => {
    const blocked = { ...ghn, fee: 0, deliverable: false, message: 'GHN không giao tới đây' };
    expect(shippingDisplay({ state: 'ready', quote: blocked, mode })).toMatchObject({ fee: 0, label: 'Phí GHN', note: 'GHN không giao tới đây', busy: false });
  });
  it('shows a TABLE fallback quote with the server message', () => {
    expect(shippingDisplay({ state: 'ready', quote: { ...table, message: 'Không kết nối được GHN, dùng phí tạm tính' }, mode }))
      .toMatchObject({ label: 'Phí tạm tính', note: 'Không kết nối được GHN, dùng phí tạm tính' });
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

const two = {
  fee: 32000, source: 'GHTK', estimated: false, weightGrams: 600, deliverable: true, message: null,
  options: [
    { source: 'GHTK', fee: 32000, estimated: false },
    { source: 'GHN', fee: 38500, estimated: false },
  ],
};
const one = { ...ghn, options: [{ source: 'GHN', fee: 28000, estimated: false }] };

describe('carrier options', () => {
  it('quoteOptions keeps live carriers only and tolerates a missing list', () => {
    expect(quoteOptions(two).map((o) => o.source)).toEqual(['GHTK', 'GHN']);
    expect(quoteOptions({ ...two, options: [{ source: 'TABLE', fee: 1, estimated: true }] })).toEqual([]);
    expect(quoteOptions(ghn)).toEqual([]);
    expect(quoteOptions(null)).toEqual([]);
  });

  it('there is a choice only with two or more options', () => {
    expect(hasCarrierChoice(two)).toBe(true);
    expect(hasCarrierChoice(one)).toBe(false);
    expect(hasCarrierChoice(table)).toBe(false);
  });

  it('chosenOption keeps the pick while it is offered, else the cheapest', () => {
    expect(chosenOption(two, null).source).toBe('GHTK');
    expect(chosenOption(two, 'GHN').source).toBe('GHN');
    expect(chosenOption(one, 'GHTK').source).toBe('GHN'); // picked carrier no longer offered
    expect(chosenOption(table, 'GHN')).toBeNull();
  });

  it('applyCarrier makes the summary follow the pick and leaves single quotes alone', () => {
    expect(applyCarrier(two, 'GHN')).toMatchObject({ source: 'GHN', fee: 38500, estimated: false, weightGrams: 600 });
    expect(applyCarrier(two, null)).toMatchObject({ source: 'GHTK', fee: 32000 });
    expect(applyCarrier(one, 'GHN')).toBe(one);
    expect(applyCarrier(null, 'GHN')).toBeNull();
  });

  it('carrierForOrder is sent only when the customer saw a choice', () => {
    expect(carrierForOrder(two, 'GHN')).toBe('GHN');
    expect(carrierForOrder(two, null)).toBe('GHTK');
    expect(carrierForOrder(one, 'GHN')).toBeUndefined();
    expect(carrierForOrder(table, null)).toBeUndefined();
  });

  it('the summary total follows the picked carrier', () => {
    const picked = applyCarrier(two, 'GHN');
    const ship = shippingDisplay({ state: 'ready', quote: picked, tableFee: 0, province: '', mode: 'GHN_IDS' });
    expect(ship.fee).toBe(38500);
    expect(ship.label).toBe('Phí GHN');
    expect(orderTotal(500000, ship.fee)).toBe(538500);
  });
});
