import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client.js';
import { useAuth } from './AuthContext.jsx';

const EMPTY = { items: [], subtotal: 0, totalQuantity: 0 };
const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { session } = useAuth();
  const [cart, setCart] = useState(EMPTY);
  // False until the cart of the current session has been fetched once, so pages can show a skeleton instead of "empty".
  const [loaded, setLoaded] = useState(false);

  const reload = useCallback(async () => {
    if (!session) {
      setCart(EMPTY);
      return;
    }
    try {
      setCart(await api('GET', '/cart'));
    } catch {
      // keep the previous cart on a transient error
    } finally {
      setLoaded(true);
    }
  }, [session]);

  const username = session?.username;
  // A different user (or logout) starts from "not loaded"; a silent token refresh must not.
  useEffect(() => { setLoaded(false); }, [username]);
  useEffect(() => { reload(); }, [reload]);

  const add = useCallback(async (variantId, quantity) => setCart(await api('POST', '/cart/items', { variantId, quantity })), []);
  const update = useCallback(async (variantId, quantity) => setCart(await api('PUT', `/cart/items/${variantId}`, { variantId, quantity })), []);
  const remove = useCallback(async (variantId) => setCart(await api('DELETE', `/cart/items/${variantId}`)), []);

  const value = useMemo(() => ({ cart, loaded, add, update, remove, reload }), [cart, loaded, add, update, remove, reload]);
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  return useContext(CartContext);
}
