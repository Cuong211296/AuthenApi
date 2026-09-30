import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client.js';
import { useAuth } from './AuthContext.jsx';

const EMPTY = { items: [], subtotal: 0, totalQuantity: 0 };
const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { session } = useAuth();
  const [cart, setCart] = useState(EMPTY);

  const reload = useCallback(async () => {
    if (!session) return setCart(EMPTY);
    try {
      setCart(await api('GET', '/cart'));
    } catch {
      setCart(EMPTY);
    }
  }, [session]);

  useEffect(() => { reload(); }, [reload]);

  const add = useCallback(async (variantId, quantity) => setCart(await api('POST', '/cart/items', { variantId, quantity })), []);
  const update = useCallback(async (variantId, quantity) => setCart(await api('PUT', `/cart/items/${variantId}`, { variantId, quantity })), []);
  const remove = useCallback(async (variantId) => setCart(await api('DELETE', `/cart/items/${variantId}`)), []);

  const value = useMemo(() => ({ cart, add, update, remove, reload }), [cart, add, update, remove, reload]);
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  return useContext(CartContext);
}
