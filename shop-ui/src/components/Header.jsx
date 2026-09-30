import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export default function Header({ cartCount = 0 }) {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/');
  }

  return (
    <header className="header">
      <div className="container header-inner">
        <Link to="/" className="brand">Cửa hàng thời trang</Link>
        <nav className="nav">
          <NavLink to="/">Sản phẩm</NavLink>
          {session && <NavLink to="/cart">Giỏ hàng{cartCount > 0 ? ` (${cartCount})` : ''}</NavLink>}
          {session && <NavLink to="/orders">Đơn hàng</NavLink>}
          {session?.isAdmin && <NavLink to="/admin/products">Quản trị</NavLink>}
        </nav>
        <div className="nav">
          {session ? (
            <>
              <span className="muted">Xin chào, {session.username}</span>
              <button className="btn btn-link" onClick={handleLogout}>Đăng xuất</button>
            </>
          ) : (
            <>
              <NavLink to="/login">Đăng nhập</NavLink>
              <NavLink to="/signup">Đăng ký</NavLink>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
