import { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import Button from '../components/ui/Button.jsx';
import Field from '../components/ui/Field.jsx';
import { AlertIcon } from '../components/ui/icons.jsx';
import { signupErrors } from '../utils/authForm.js';
import AuthLayout from './auth/AuthLayout.jsx';

const FIELD_ORDER = ['username', 'password', 'lastname', 'firstname', 'dob'];

export default function Signup() {
  const { session, signup } = useAuth();
  const [form, setForm] = useState({ username: '', password: '', firstname: '', lastname: '', dob: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const set = (key) => (e) => { const { value } = e.target; setForm((f) => ({ ...f, [key]: value })); };
  const blur = (key) => () => setTouched((t) => ({ ...t, [key]: true }));

  const errors = signupErrors(form);
  const shown = (key) => (submitted || touched[key] ? errors[key] : undefined);

  // Signed in (including right after signup, which logs in automatically): go home.
  if (session) return <Navigate to="/" replace />;

  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    setError('');
    setSubmitted(true);
    const first = FIELD_ORDER.find((key) => errors[key]);
    if (first) return document.getElementById(`signup-${first}`)?.focus();
    setBusy(true);
    try {
      await signup({ ...form, username: form.username.trim() });
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      eyebrow="Thành viên mới"
      title="Đăng ký"
      lead="Tạo tài khoản trong vài giây để đặt hàng và theo dõi đơn."
      footer={<>Đã có tài khoản? <Link to="/login">Đăng nhập</Link></>}
    >
      <form className="au-form" onSubmit={submit} noValidate>
        {error && <p className="au-alert" role="alert"><AlertIcon size={18} /><span>{error}</span></p>}
        <Field
          id="signup-username" label="Tên đăng nhập" value={form.username} onChange={set('username')}
          onBlur={blur('username')} error={shown('username')} autoComplete="username" required
        />
        <Field
          id="signup-password" label="Mật khẩu" type="password" value={form.password} onChange={set('password')}
          onBlur={blur('password')} error={shown('password')} hint="Tối thiểu 8 ký tự" autoComplete="new-password" required
        />
        <div className="au-form__row">
          <Field
            id="signup-lastname" label="Họ" value={form.lastname} onChange={set('lastname')}
            onBlur={blur('lastname')} error={shown('lastname')} autoComplete="family-name" required
          />
          <Field
            id="signup-firstname" label="Tên" value={form.firstname} onChange={set('firstname')}
            onBlur={blur('firstname')} error={shown('firstname')} autoComplete="given-name" required
          />
        </div>
        <Field
          id="signup-dob" label="Ngày sinh" type="date" value={form.dob} onChange={set('dob')}
          onBlur={blur('dob')} error={shown('dob')} autoComplete="bday" required
        />
        <Button type="submit" size="lg" block loading={busy}>{busy ? 'Đang tạo tài khoản...' : 'Tạo tài khoản'}</Button>
      </form>
    </AuthLayout>
  );
}
