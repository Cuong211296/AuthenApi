import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from;
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login(username.trim(), password);
      navigate(from ? from.pathname + (from.search || '') : '/', { replace: true });
    } catch (err) {
      setError(err.status === 401 ? 'Sai tên đăng nhập hoặc mật khẩu' : err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card form narrow" onSubmit={submit}>
      <h1>Đăng nhập</h1>
      {error && <p className="alert alert-error">{error}</p>}
      <label>Tên đăng nhập
        <input value={username} onChange={(e) => setUsername(e.target.value)} required autoComplete="username" />
      </label>
      <label>Mật khẩu
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
      </label>
      <button className="btn btn-primary" disabled={busy}>{busy ? 'Đang đăng nhập...' : 'Đăng nhập'}</button>
      <p className="muted">Chưa có tài khoản? <Link to="/signup">Đăng ký</Link></p>
    </form>
  );
}
