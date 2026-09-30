import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import Button from '../components/ui/Button.jsx';
import Field from '../components/ui/Field.jsx';
import { AlertIcon } from '../components/ui/icons.jsx';
import { loginErrors } from '../utils/authForm.js';
import AuthLayout from './auth/AuthLayout.jsx';

export default function Login() {
  const { session, login } = useAuth();
  const location = useLocation();
  const from = location.state?.from;
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);

  const errors = loginErrors({ username, password });
  const shown = (key) => (submitted || touched[key] ? errors[key] : undefined);
  const blur = (key) => () => setTouched((t) => ({ ...t, [key]: true }));

  // Signed in (including right after a successful login): go back to where the user came from, else home.
  if (session) return <Navigate to={from ? from.pathname + (from.search || '') : '/'} replace />;

  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    setError('');
    setSubmitted(true);
    const first = ['username', 'password'].find((key) => errors[key]);
    if (first) return document.getElementById(`login-${first}`)?.focus();
    setBusy(true);
    try {
      await login(username.trim(), password);
    } catch (err) {
      setError(err.status === 401 ? 'Sai tên đăng nhập hoặc mật khẩu' : err.message);
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      eyebrow="Chào mừng trở lại"
      title="Đăng nhập"
      lead="Đăng nhập để xem giỏ hàng và theo dõi đơn hàng của bạn."
      footer={<>Chưa có tài khoản? <Link to="/signup">Đăng ký</Link></>}
    >
      <form className="au-form" onSubmit={submit} noValidate>
        {error && <p className="au-alert" role="alert"><AlertIcon size={18} /><span>{error}</span></p>}
        <Field
          id="login-username" label="Tên đăng nhập" value={username} onChange={(e) => setUsername(e.target.value)}
          onBlur={blur('username')} error={shown('username')} autoComplete="username" required
        />
        <Field
          id="login-password" label="Mật khẩu" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
          onBlur={blur('password')} error={shown('password')} autoComplete="current-password" required
        />
        <Button type="submit" size="lg" block loading={busy}>{busy ? 'Đang đăng nhập...' : 'Đăng nhập'}</Button>
      </form>
    </AuthLayout>
  );
}
