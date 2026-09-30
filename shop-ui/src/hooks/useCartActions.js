import { useCallback, useState } from 'react';
import { useCart } from '../context/CartContext.jsx';

/**
 * Cart mutations shared by the cart page and the cart drawer. Every action goes through `run()`, which clears the
 * previous error, records the server message on failure and resolves to whether it succeeded (so a rejected quantity
 * can reset the value the shopper sees). `pendingId` is the variant currently being changed.
 */
export function useCartActions() {
  const { update, remove } = useCart();
  const [error, setError] = useState('');
  const [pendingId, setPendingId] = useState(null);

  const run = useCallback(async (variantId, fn) => {
    setError('');
    setPendingId(variantId);
    try {
      await fn();
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    } finally {
      setPendingId(null);
    }
  }, []);

  const changeQuantity = useCallback((variantId, quantity) => run(variantId, () => update(variantId, quantity)), [run, update]);
  const removeLine = useCallback((variantId) => run(variantId, () => remove(variantId)), [run, remove]);

  return { error, clearError: () => setError(''), pendingId, changeQuantity, removeLine };
}
