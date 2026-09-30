import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export default function Signup() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', password: '', firstname: '', lastname: '', dob: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (form.username.trim().length < 3) return setError('Tên đăng nhập tối thiểu 3 ký tự');
    if (form.password.length < 8) return setError('Mật khẩu tối thiểu 8 ký tự');
    setBusy(true);
    try {
      await signup({ ...form, username: form.username.trim() });
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card form narrow" onSubmit={submit}>
      <h1>Đăng ký</h1>
      {error && <p className="alert alert-error">{error}</p>}
      <label>Tên đăng nhập<input value={form.username} onChange={set('username')} required autoComplete="username" /></label>
      <label>Mật khẩu<input type="password" value={form.password} onChange={set('password')} required autoComplete="new-password" /></label>
      <label>Họ<input value={form.lastname} onChange={set('lastname')} required /></label>
      <label>Tên<input value={form.firstname} onChange={set('firstname')} required /></label>
      <label>Ngày sinh<input type="date" value={form.dob} onChange={set('dob')} required /></label>
      <button className="btn btn-primary" disabled={busy}>{busy ? 'Đang tạo tài khoản...' : 'Đăng ký'}</button>
      <p className="muted">Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
    </form>
  );
}
