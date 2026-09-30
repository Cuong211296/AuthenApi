import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';

const CartDrawerContext = createContext(null);

/** Open/close state of the cart drawer, shared by the header cart button and the product page toast action. */
export function CartDrawerProvider({ children }) {
  const [open, setOpen] = useState(false);
  const location = useLocation();

  const openCart = useCallback(() => setOpen(true), []);
  const closeCart = useCallback(() => setOpen(false), []);

  // Any navigation (a link inside the drawer, browser back) closes it.
  useEffect(() => setOpen(false), [location.pathname]);

  const value = useMemo(() => ({ open, openCart, closeCart }), [open, openCart, closeCart]);
  return <CartDrawerContext.Provider value={value}>{children}</CartDrawerContext.Provider>;
}

export function useCartDrawer() {
  const ctx = useContext(CartDrawerContext);
  if (!ctx) throw new Error('useCartDrawer must be used inside <CartDrawerProvider>');
  return ctx;
}
