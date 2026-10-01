import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { LayoutGroup, motion, useReducedMotion } from 'framer-motion';
import { BRAND } from '../../components/layout/brand.js';
import { BoxIcon, ChartIcon, ReceiptIcon, StoreIcon, TruckIcon } from '../../components/ui/icons.jsx';
import { SPRING } from '../../motion/variants.js';
import AdminOrders from './AdminOrders.jsx';
import AdminOverview from './AdminOverview.jsx';
import AdminProducts from './AdminProducts.jsx';
import AdminShipping from './AdminShipping.jsx';
import './Admin.css';

const NAV = [
  { to: '/admin/overview', label: 'Tổng quan', Icon: ChartIcon },
  { to: '/admin/products', label: 'Sản phẩm', Icon: BoxIcon },
  { to: '/admin/orders', label: 'Đơn hàng', Icon: ReceiptIcon },
  { to: '/admin/shipping', label: 'Phí vận chuyển', Icon: TruckIcon },
];

export default function AdminLayout() {
  const reduce = useReducedMotion();
  return (
    <div className="ad">
      <aside className="ad__side">
        <div className="ad__brand">
          <span className="eyebrow">Quản trị</span>
          <span className="ad__brand-name">{BRAND.first}<i>·</i>{BRAND.second}</span>
        </div>
        <nav className="ad__nav" aria-label="Quản trị">
          <LayoutGroup id="admin-nav">
            {NAV.map(({ to, label, Icon }) => (
              <NavLink key={to} to={to} className="ad__link">
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <motion.span
                        layoutId="ad-nav-indicator"
                        className="ad__link-bg"
                        transition={reduce ? { duration: 0 } : SPRING}
                        aria-hidden="true"
                      />
                    )}
                    <Icon size={20} className="ad__link-icon" />
                    <span className="ad__link-text">{label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </LayoutGroup>
        </nav>
        <NavLink to="/" className="ad__store">
          <StoreIcon size={20} />
          <span>Về cửa hàng</span>
        </NavLink>
      </aside>
      <div className="ad__content">
        <Routes>
          <Route path="overview" element={<AdminOverview />} />
          <Route path="products" element={<AdminProducts />} />
          <Route path="orders" element={<AdminOrders />} />
          <Route path="shipping" element={<AdminShipping />} />
          <Route path="*" element={<Navigate to="overview" replace />} />
        </Routes>
      </div>
    </div>
  );
}
