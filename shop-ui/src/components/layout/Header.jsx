import { useEffect, useId, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { useAuth } from '../../context/AuthContext.jsx';
import { useCart } from '../../context/CartContext.jsx';
import { useCartDrawer } from '../../context/CartDrawerContext.jsx';
import { useCategories } from '../../hooks/useCategories.js';
import Drawer from '../ui/Drawer.jsx';
import { CartIcon, ChevronIcon, MenuIcon, SearchIcon, UserIcon } from '../ui/icons.jsx';
import { EASE } from '../../motion/variants.js';
import { BRAND } from './brand.js';
import '../ui/Button.css';
import './Header.css';

export const SEARCH_INPUT_ID = 'product-search';

function useScrolled(threshold = 8) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > threshold);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [threshold]);
  return scrolled;
}

function CartButton({ count }) {
  const reduce = useReducedMotion();
  const { open, openCart } = useCartDrawer();
  const label = count > 0 ? `Giỏ hàng, ${count} sản phẩm` : 'Giỏ hàng';
  return (
    <button type="button" className="ui-icon-btn hdr-cart" aria-label={label} aria-haspopup="dialog" aria-expanded={open} onClick={openCart}>
      <CartIcon size={22} />
      <AnimatePresence initial={false}>
        {count > 0 && (
          <motion.span
            key={count}
            className="hdr-cart__badge tabular"
            aria-hidden="true"
            initial={reduce ? { opacity: 0 } : { scale: 0.4, opacity: 0 }}
            animate={reduce ? { opacity: 1 } : { scale: [1.35, 1], opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 0.1 } }}
            transition={{ duration: 0.35, ease: EASE }}
          >
            {count > 99 ? '99+' : count}
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  );
}

function UserMenu({ session, onLogout }) {
  const [open, setOpen] = useState(false);
  const menuId = useId();
  const rootRef = useRef(null);
  const buttonRef = useRef(null);
  const location = useLocation();

  useEffect(() => setOpen(false), [location.pathname]);

  useEffect(() => {
    if (!open) return undefined;
    const onPointer = (e) => { if (!rootRef.current?.contains(e.target)) setOpen(false); };
    const onKey = (e) => {
      if (e.key === 'Escape') { setOpen(false); buttonRef.current?.focus(); }
    };
    document.addEventListener('pointerdown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="hdr-user" ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        className="hdr-user__btn"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((o) => !o)}
      >
        <span className="hdr-user__avatar" aria-hidden="true">{session.username.slice(0, 1).toUpperCase()}</span>
        <span className="hdr-user__name">{session.username}</span>
        <ChevronIcon size={16} className={`hdr-user__chev ${open ? 'is-open' : ''}`} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            id={menuId}
            className="hdr-user__menu"
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1, transition: { duration: 0.2, ease: EASE } }}
            exit={{ opacity: 0, y: -4, transition: { duration: 0.12 } }}
          >
            <p className="hdr-user__hello">Xin chào, <strong>{session.username}</strong></p>
            <ul>
              <li><Link to="/orders">Đơn hàng của tôi</Link></li>
              <li><Link to="/cart">Giỏ hàng</Link></li>
              {session.isAdmin && <li><Link to="/admin">Trang quản trị</Link></li>}
              <li><button type="button" onClick={onLogout}>Đăng xuất</button></li>
            </ul>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default function Header() {
  const { session, logout } = useAuth();
  const { cart } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const scrolled = useScrolled();
  const categories = useCategories();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => setMenuOpen(false), [location.pathname, location.search]);

  async function handleLogout() {
    setMenuOpen(false);
    await logout();
    navigate('/');
  }

  function goSearch() {
    const input = document.getElementById(SEARCH_INPUT_ID);
    if (location.pathname === '/' && input) {
      input.scrollIntoView({ block: 'center' });
      input.focus({ preventScroll: true });
    } else {
      navigate('/?focus=search');
    }
  }

  const onHome = location.pathname === '/';
  const activeCategory = onHome ? new URLSearchParams(location.search).get('category') || '' : null;
  const links = [
    { to: '/', label: 'Cửa hàng', active: activeCategory === '' },
    ...categories.slice(0, 3).map((c) => ({ to: `/?category=${c.slug}`, label: c.name, active: activeCategory === c.slug })),
    ...(session ? [{ to: '/orders', label: 'Đơn hàng', active: location.pathname.startsWith('/orders') }] : []),
    ...(session?.isAdmin ? [{ to: '/admin', label: 'Quản trị', active: location.pathname.startsWith('/admin') }] : []),
  ];

  return (
    <header className={`hdr ${scrolled ? 'is-scrolled' : ''}`}>
      <div className="container hdr__inner">
        <Link to="/" className="hdr__brand" aria-label={`${BRAND.full}, trang chủ`}>
          {BRAND.first}<span className="hdr__brand-dot" aria-hidden="true">·</span><em>{BRAND.second}</em>
        </Link>

        <nav className="hdr__nav" aria-label="Điều hướng chính">
          {links.map((l) => (
            <Link key={l.to} to={l.to} className={`hdr__link ${l.active ? 'is-active' : ''}`} aria-current={l.active ? 'page' : undefined}>
              {l.label}
            </Link>
          ))}
        </nav>

        <div className="hdr__actions">
          <button type="button" className="ui-icon-btn" aria-label="Tìm kiếm sản phẩm" onClick={goSearch}>
            <SearchIcon size={21} />
          </button>
          <CartButton count={session ? cart.totalQuantity : 0} />
          <div className="hdr__desktop-only">
            {session ? (
              <UserMenu session={session} onLogout={handleLogout} />
            ) : (
              <div className="hdr__auth">
                <Link to="/login" className="hdr__link">Đăng nhập</Link>
                <Link to="/signup" className="hdr__signup">Đăng ký</Link>
              </div>
            )}
          </div>
          <button
            type="button"
            className="ui-icon-btn hdr__burger"
            aria-label="Mở menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen(true)}
          >
            <MenuIcon size={22} />
          </button>
        </div>
      </div>

      <Drawer open={menuOpen} onClose={() => setMenuOpen(false)} title="Menu" className="hdr-mobile">
        <nav aria-label="Điều hướng di động" className="hdr-mobile__nav">
          {links.map((l) => (
            <Link key={l.to} to={l.to} aria-current={l.active ? 'page' : undefined}>{l.label}</Link>
          ))}
          {session && <Link to="/cart">Giỏ hàng{cart.totalQuantity > 0 ? ` (${cart.totalQuantity})` : ''}</Link>}
        </nav>
        <div className="hdr-mobile__account">
          {session ? (
            <>
              <p className="hdr-mobile__who"><UserIcon size={18} /> Đăng nhập với <strong>{session.username}</strong></p>
              <button type="button" className="hdr-mobile__logout" onClick={handleLogout}>Đăng xuất</button>
            </>
          ) : (
            <div className="hdr-mobile__cta">
              <Link to="/login" className="ui-btn ui-btn--dark ui-btn--md ui-btn--block">Đăng nhập</Link>
              <Link to="/signup" className="ui-btn ui-btn--ghost ui-btn--md ui-btn--block">Tạo tài khoản</Link>
            </div>
          )}
        </div>
      </Drawer>
    </header>
  );
}
