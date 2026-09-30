import { Link, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext.jsx';
import { CartProvider } from './context/CartContext.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import AppShell from './components/layout/AppShell.jsx';
import PageTransition from './components/ui/PageTransition.jsx';
import EmptyState from './components/ui/EmptyState.jsx';
import Button from './components/ui/Button.jsx';
import { ToastProvider } from './components/ui/Toast.jsx';
import { SearchIcon } from './components/ui/icons.jsx';
import Login from './pages/Login.jsx';
import Signup from './pages/Signup.jsx';
import Home from './pages/Home.jsx';
import ProductDetail from './pages/ProductDetail.jsx';
import Cart from './pages/Cart.jsx';
import Checkout from './pages/Checkout.jsx';
import PaymentResult from './pages/PaymentResult.jsx';
import Orders from './pages/Orders.jsx';
import OrderDetail from './pages/OrderDetail.jsx';
import AdminLayout from './pages/admin/AdminLayout.jsx';

/** Pages not yet redesigned keep their old markup inside a padded container (styles/legacy.css). */
const legacy = (element) => <div className="container legacy-page">{element}</div>;

function NotFound() {
  return (
    <div className="container legacy-page">
      <EmptyState
        icon={<SearchIcon size={26} />}
        title="Không tìm thấy trang"
        action={<Button as={Link} to="/" variant="dark">Về trang chủ</Button>}
      >
        Trang bạn tìm có thể đã được di chuyển hoặc không còn tồn tại.
      </EmptyState>
    </div>
  );
}

/** Admin sub-pages share one key so switching admin tabs does not replay the page transition. */
const transitionKey = (pathname) => (pathname.startsWith('/admin') ? '/admin' : pathname);

function AppRoutes() {
  return (
    <AppShell>
      {(location) => (
        <PageTransition key={transitionKey(location.pathname)}>
          <Routes location={location}>
            <Route path="/" element={<Home />} />
            <Route path="/products/:slug" element={legacy(<ProductDetail />)} />
            <Route path="/cart" element={<ProtectedRoute>{legacy(<Cart />)}</ProtectedRoute>} />
            <Route path="/checkout" element={<ProtectedRoute>{legacy(<Checkout />)}</ProtectedRoute>} />
            <Route path="/payment/result" element={<ProtectedRoute>{legacy(<PaymentResult />)}</ProtectedRoute>} />
            <Route path="/orders" element={<ProtectedRoute>{legacy(<Orders />)}</ProtectedRoute>} />
            <Route path="/orders/:code" element={<ProtectedRoute>{legacy(<OrderDetail />)}</ProtectedRoute>} />
            <Route path="/login" element={legacy(<Login />)} />
            <Route path="/signup" element={legacy(<Signup />)} />
            <Route path="/admin/*" element={<ProtectedRoute admin>{legacy(<AdminLayout />)}</ProtectedRoute>} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </PageTransition>
      )}
    </AppShell>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <ToastProvider>
          <AppRoutes />
        </ToastProvider>
      </CartProvider>
    </AuthProvider>
  );
}
