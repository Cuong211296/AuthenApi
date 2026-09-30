import { useCallback, useRef, useState } from 'react';
import { useCart } from '../context/CartContext.jsx';

/**
 * Cart mutations shared by the cart page and the cart drawer. Every action goes through `run()`, which clears the
 * previous error, records the server message on failure and resolves to whether it succeeded (so a rejected quantity
 * can reset the value the shopper sees). `pendingId` is the variant being changed; `busy` is true while any change runs.
 */
export function useCartActions() {
  const { update, remove } = useCart();
  const [error, setError] = useState('');
  const [pendingId, setPendingId] = useState(null);
  const inFlight = useRef(false);

  // One change at a time: while a request is running every line is disabled (`busy`), so responses cannot interleave.
  const run = useCallback(async (variantId, fn) => {
    if (inFlight.current) return false;
    inFlight.current = true;
    setError('');
    setPendingId(variantId);
    try {
      await fn();
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    } finally {
      inFlight.current = false;
      setPendingId(null);
    }
  }, []);

  const changeQuantity = useCallback((variantId, quantity) => run(variantId, () => update(variantId, quantity)), [run, update]);
  const removeLine = useCallback((variantId) => run(variantId, () => remove(variantId)), [run, remove]);

  const clearError = useCallback(() => setError(''), []);
  return { error, clearError, pendingId, busy: pendingId !== null, changeQuantity, removeLine };
}
