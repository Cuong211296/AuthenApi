/** Pure helpers for the checkout shipping quote (GHTK with a fixed-table fallback). */

export const NOT_DELIVERABLE_MESSAGE = 'Địa chỉ này chưa hỗ trợ giao hàng. Hãy kiểm tra lại phường/xã và địa chỉ.';
export const IDLE_NOTE = 'Nhập phường/xã để tính phí chính xác';
export const QUOTE_FAILED_NOTE = 'Không tính được phí chính xác, sẽ chốt khi đặt hàng';

/** A quote can be requested once the province and the ward are filled in (address is optional for the server). */
export function quoteInputsReady(form) {
  return Boolean(String(form?.province ?? '').trim() && String(form?.ward ?? '').trim());
}

/** Badge text/tone and the server's explanation for a quote response. Only an exact GHTK fee gets the GHTK label. */
export function describeQuote(quote) {
  const exact = quote?.source === 'GHTK' && !quote?.estimated;
  return {
    label: exact ? 'Phí GHTK' : 'Phí tạm tính',
    tone: exact ? 'accent' : 'warn',
    note: typeof quote?.message === 'string' ? quote.message.trim() : '',
  };
}

/** Label of a stored order's shippingSource ('GHTK' | 'TABLE' | null for old orders); null when unknown. */
export function shippingSourceLabel(source) {
  if (source === 'GHTK') return { label: 'Phí GHTK', tone: 'accent' };
  if (source === 'TABLE') return { label: 'Phí tạm tính', tone: 'warn' };
  return null;
}

/** 600 -> "0,6 kg"; null/invalid -> ''. */
export function formatWeight(grams) {
  const g = Number(grams);
  if (!Number.isFinite(g) || g <= 0) return '';
  return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(g / 1000)} kg`;
}

/**
 * Which shipping fee the summary shows.
 * ready -> the quote; loading -> the previous quote (dimmed) or the province table fee; idle/error -> the table fee.
 * Returns { fee, label, tone, note, weightGrams, provisional, busy } or null while no province is chosen.
 */
export function shippingDisplay({ state, quote, tableFee, province }) {
  if (!province) return null;
  if (state === 'ready' && quote) {
    const d = describeQuote(quote);
    return { fee: quote.fee, ...d, weightGrams: quote.weightGrams ?? null, provisional: false, busy: false };
  }
  if (state === 'loading' && quote) {
    return { fee: quote.fee, ...describeQuote(quote), weightGrams: quote.weightGrams ?? null, provisional: false, busy: true };
  }
  return {
    fee: tableFee ?? 0,
    label: 'Phí tạm tính',
    tone: 'warn',
    note: state === 'error' ? QUOTE_FAILED_NOTE : state === 'idle' ? IDLE_NOTE : '',
    weightGrams: null,
    provisional: true,
    busy: state === 'loading',
  };
}

/** Friendly message for a failed quote request. */
export function quoteErrorMessage(err) {
  if (err?.code === 2009) return 'Tỉnh/thành này chưa được hỗ trợ giao hàng.';
  if (err?.code === 2008) return 'Giỏ hàng đang trống.';
  return QUOTE_FAILED_NOTE;
}

/** Order total: subtotal plus shipping (a missing fee counts as 0). */
export const orderTotal = (subtotal, fee) => (Number(subtotal) || 0) + (Number(fee) || 0);

/** Stable key that changes whenever the cart contents change (total quantity + subtotal). */
export const cartKeyOf = (cart) => `${cart?.totalQuantity ?? (cart?.items || []).reduce((n, i) => n + i.quantity, 0)}:${cart?.subtotal ?? 0}`;
