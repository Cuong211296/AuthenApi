import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import AdminOrders from './AdminOrders.jsx';
import AdminProducts from './AdminProducts.jsx';
import AdminShipping from './AdminShipping.jsx';

export default function AdminLayout() {
  return (
    <>
      <div className="chips">
        <NavLink className="chip" to="/admin/products">Sản phẩm</NavLink>
        <NavLink className="chip" to="/admin/orders">Đơn hàng</NavLink>
        <NavLink className="chip" to="/admin/shipping">Phí vận chuyển</NavLink>
      </div>
      <Routes>
        <Route path="products" element={<AdminProducts />} />
        <Route path="orders" element={<AdminOrders />} />
        <Route path="shipping" element={<AdminShipping />} />
        <Route path="*" element={<Navigate to="products" replace />} />
      </Routes>
    </>
  );
}
