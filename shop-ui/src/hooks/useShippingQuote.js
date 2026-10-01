import { useEffect, useState } from 'react';
import { api } from '../api/client.js';

const DEBOUNCE_MS = 600;
const IDLE = { state: 'idle', quote: null, error: null };

/**
 * Asks the server for the shipping fee of the current cart and address.
 * `address` is the quote body from `buildAddressPayload` (ids + names, or province/ward/address); `enabled` is true
 * once the mode's required fields are complete. Debounced until the inputs settle, ignores stale responses,
 * refetches when `cartKey` changes. While a new quote loads the previous one is kept so the UI can dim it.
 * Returns { state: 'idle' | 'loading' | 'ready' | 'error', quote, error }.
 */
export function useShippingQuote({ address, cartKey, enabled }) {
  const [result, setResult] = useState(IDLE);
  const body = JSON.stringify(address ?? {});

  useEffect(() => {
    if (!enabled) {
      setResult(IDLE);
      return undefined;
    }
    let ignore = false;
    setResult((prev) => ({ ...prev, state: 'loading', error: null }));
    const timer = setTimeout(() => {
      api('POST', '/shipping/quote', JSON.parse(body))
        .then((quote) => { if (!ignore) setResult({ state: 'ready', quote, error: null }); })
        .catch((error) => { if (!ignore) setResult({ state: 'error', quote: null, error }); });
    }, DEBOUNCE_MS);
    return () => { ignore = true; clearTimeout(timer); };
  }, [enabled, body, cartKey]);

  return result;
}
