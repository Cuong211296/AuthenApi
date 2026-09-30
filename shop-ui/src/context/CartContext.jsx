import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client.js';
import { useAuth } from './AuthContext.jsx';

const EMPTY = { items: [], subtotal: 0, totalQuantity: 0 };
const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { session } = useAuth();
  const [cart, setCart] = useState(EMPTY);
  // False until the cart of the current session has been fetched once, so pages can show a skeleton instead of "empty".
  const [loaded, setLoaded] = useState(false);

  // Only the most recent request may write the cart (older, slower responses are dropped), and a user change
  // invalidates everything in flight.
  const seq = useRef(0);
  const latest = useCallback(async (request) => {
    const mine = ++seq.current;
    const result = await request();
    if (mine === seq.current) {
      setCart(result);
      setLoaded(true);
    }
    return result;
  }, []);

  const reload = useCallback(async () => {
    if (!session) {
      seq.current += 1;
      setCart(EMPTY);
      return;
    }
    const mine = seq.current + 1;
    try {
      await latest(() => api('GET', '/cart'));
    } catch {
      // keep the previous cart on a transient error
    } finally {
      if (mine === seq.current) setLoaded(true);
    }
  }, [session, latest]);

  const username = session?.username;
  // A different user (or logout) starts from "not loaded"; a silent token refresh must not.
  useEffect(() => {
    seq.current += 1;
    setLoaded(false);
  }, [username]);
  useEffect(() => { reload(); }, [reload]);

  const add = useCallback(async (variantId, quantity) => { await latest(() => api('POST', '/cart/items', { variantId, quantity })); }, [latest]);
  const update = useCallback(async (variantId, quantity) => { await latest(() => api('PUT', `/cart/items/${variantId}`, { variantId, quantity })); }, [latest]);
  const remove = useCallback(async (variantId) => { await latest(() => api('DELETE', `/cart/items/${variantId}`)); }, [latest]);

  const value = useMemo(() => ({ cart, loaded, add, update, remove, reload }), [cart, loaded, add, update, remove, reload]);
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  return useContext(CartContext);
}
