# Clothing Shop - Plan 4: React Frontend

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A React + Vite storefront (browse, product detail with size/color, cart, checkout with COD or MoMo, payment result, my orders) plus an admin area (products/variants, orders, shipping fees), replacing the old static login/signup/dashboard pages.

**Architecture:** Single-page app in `shop-ui/`. One `api/client.js` unwraps the backend `ApiResponse`, attaches the JWT, and refreshes the token proactively. `AuthContext` and `CartContext` hold state; pages are thin and call the API through the client. Pure helpers (`money`, `variants`, `labels`) are unit-tested with Vitest.

**Tech Stack:** Node 20, React 18, Vite 5, React Router 6, Vitest + jsdom. Plain JavaScript and one CSS file.

**Spec:** `docs/superpowers/specs/2026-09-30-clothing-shop-design.md` (section 6)

**Prerequisite:** Plans 1-3 complete and the backend running on `http://localhost:8081/identity` (`mvn spring-boot:run` from the project root, with `.env` filled in). Node 20 installed (`node -v` shows v20.x).

## Global Constraints

- API base URL from `VITE_API_BASE`, default `http://localhost:8081/identity`. Dev server must run on port **5173** (`strictPort`): the backend CORS list and the MoMo `redirectUrl` (`FRONTEND_URL`, default `http://localhost:5173`) depend on it.
- Success responses have `code === 1000`; anything else is an error whose `message` is shown to the user.
- JWT stored in `localStorage` under `auth_token` (same key as the old pages). Refresh proactively when fewer than 300 seconds remain (the backend refresh endpoint rejects expired tokens, so refresh-on-401 cannot work).
- Money shown as Vietnamese dong with `Intl.NumberFormat('vi-VN')`; UI text in Vietnamese.
- The customer never sees or sends prices to the server at checkout; the server recomputes.
- Commits end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- Token expiring while the app is open: the session ends cleanly and the user lands on login, not on a broken page (Task 1 client tests, Task 2).
- Two requests firing when the token is about to expire refresh it only once (Task 1 test).
- Adding to cart while logged out sends the user to login and back, not to an error (Task 3).
- Stock or availability changing after items were carted: the cart marks the line unavailable and blocks checkout (Task 3, Task 4).
- MoMo order where starting the payment fails: the order still exists and the user can retry from order detail (Task 4).
- Returning from MoMo repeatedly (refresh, StrictMode double effect) is harmless (Task 4).
- Admin pages unreachable for non-admins (Task 2, Task 5).

## File Structure (all under `shop-ui/`)

| File | Responsibility |
|---|---|
| `package.json`, `vite.config.js`, `index.html`, `.env.example`, `.gitignore` | tooling |
| `src/api/client.js` | fetch wrapper, token store, refresh |
| `src/utils/money.js`, `variants.js`, `labels.js` | pure helpers |
| `src/context/AuthContext.jsx`, `CartContext.jsx` | session and cart state |
| `src/components/Header.jsx`, `ProtectedRoute.jsx`, `ProductCard.jsx` | shared UI |
| `src/pages/*.jsx`, `src/pages/admin/*.jsx` | screens |
| `src/App.jsx`, `src/main.jsx`, `src/styles.css` | wiring and styling |

---

### Task 1: Scaffold, API client, pure helpers

**Files:**
- Create: `shop-ui/package.json`, `shop-ui/vite.config.js`, `shop-ui/index.html`, `shop-ui/.env.example`, `shop-ui/.gitignore`, `shop-ui/src/main.jsx`, `shop-ui/src/App.jsx` (placeholder), `shop-ui/src/api/client.js`, `shop-ui/src/utils/money.js`, `shop-ui/src/utils/variants.js`, `shop-ui/src/utils/labels.js`
- Test: `shop-ui/src/api/client.test.js`, `shop-ui/src/utils/money.test.js`, `shop-ui/src/utils/variants.test.js`

**Interfaces:**
- Produces:
  - `api(method, path, body?, { auth = true }?) : Promise<result>`; throws `ApiError(message, code, status)`
  - `tokenStore.{get,set,clear}`, `decodeJwt(token)`, `secondsLeft(token, nowMs?)`, `TOKEN_KEY`
  - window events: `auth:expired` (session ended), `auth:refreshed` (token replaced)
  - `formatVnd(n)`; `sizesOf(variants)`, `colorsFor(variants, size)`, `findVariant(variants, size, color)`; `ORDER_STATUS_LABEL`, `PAYMENT_STATUS_LABEL`, `NEXT_STATUSES`

- [ ] **Step 1: Confirm Node 20 and create the project files**

Run: `node -v`
Expected: `v20.x.x`. If not, open a new terminal (PATH refresh) or reinstall Node 20 before continuing.

`shop-ui/package.json`:
```json
{
  "name": "shop-ui",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview --port 5173 --strictPort",
    "test": "vitest run"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.26.2"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.3.1",
    "jsdom": "^25.0.1",
    "vite": "^5.4.8",
    "vitest": "^2.1.2"
  }
}
```

`shop-ui/vite.config.js`:
```js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  test: { environment: 'jsdom', globals: true },
});
```

`shop-ui/index.html`:
```html
<!doctype html>
<html lang="vi">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Cửa hàng thời trang</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
  </body>
</html>
```

`shop-ui/.env.example`:
```
VITE_API_BASE=http://localhost:8081/identity
```

`shop-ui/.gitignore`:
```
node_modules/
dist/
.env.local
```

`shop-ui/src/main.jsx`:
```jsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.jsx';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
```

`shop-ui/src/App.jsx` (placeholder, replaced in Task 2):
```jsx
export default function App() {
  return <p>Shop UI</p>;
}
```

`shop-ui/src/styles.css` (placeholder, replaced in Task 2):
```css
body { font-family: system-ui, sans-serif; margin: 0; }
```

- [ ] **Step 2: Install dependencies**

Run (in `shop-ui/`): `npm install`
Expected: completes without errors. If a version fails to resolve, install that package with `@latest` and note it.

- [ ] **Step 3: Write the failing tests**

`shop-ui/src/utils/money.test.js`:
```js
import { formatVnd } from './money.js';

describe('formatVnd', () => {
  it('uses dots as thousands separators and the dong sign', () => {
    expect(formatVnd(150000)).toContain('150.000');
    expect(formatVnd(150000)).toContain('₫');
  });
  it('handles zero and missing values', () => {
    expect(formatVnd(0)).toContain('0');
    expect(formatVnd(undefined)).toContain('0');
  });
});
```

`shop-ui/src/utils/variants.test.js`:
```js
import { sizesOf, colorsFor, findVariant } from './variants.js';

const variants = [
  { id: '1', size: 'M', color: 'white', stock: 3 },
  { id: '2', size: 'M', color: 'black', stock: 0 },
  { id: '3', size: 'L', color: 'white', stock: 1 },
];

describe('variants helpers', () => {
  it('lists each size once in order of appearance', () => {
    expect(sizesOf(variants)).toEqual(['M', 'L']);
  });
  it('lists colors for a size and flags the ones out of stock', () => {
    expect(colorsFor(variants, 'M')).toEqual([
      { color: 'white', available: true },
      { color: 'black', available: false },
    ]);
  });
  it('finds the exact variant or undefined', () => {
    expect(findVariant(variants, 'L', 'white').id).toBe('3');
    expect(findVariant(variants, 'L', 'black')).toBeUndefined();
  });
});
```

`shop-ui/src/api/client.test.js`:
```js
import { api, ApiError, tokenStore, secondsLeft, TOKEN_KEY } from './client.js';

function makeJwt(expSeconds) {
  const b64 = (o) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${b64({ alg: 'none' })}.${b64({ sub: 'alice', exp: expSeconds, scope: 'ROLE_USER' })}.sig`;
}
const nowSec = () => Math.floor(Date.now() / 1000);

function respond(status, json) {
  return Promise.resolve({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(json) });
}

beforeEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});

describe('api client', () => {
  it('unwraps the result of a successful response', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respond(200, { code: 1000, result: { hello: 'world' } })));
    expect(await api('GET', '/x', undefined, { auth: false })).toEqual({ hello: 'world' });
  });

  it('turns a business error into ApiError with code, message and status', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respond(409, { code: 2006, message: 'Not enough stock' })));
    const err = await api('POST', '/orders', {}, { auth: false }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ code: 2006, message: 'Not enough stock', status: 409 });
  });

  it('reports network failure as ApiError with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('failed'))));
    const err = await api('GET', '/x', undefined, { auth: false }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(0);
  });

  it('sends the bearer token when authenticated', async () => {
    tokenStore.set(makeJwt(nowSec() + 3600));
    const fetchMock = vi.fn(() => respond(200, { code: 1000, result: null }));
    vi.stubGlobal('fetch', fetchMock);
    await api('GET', '/cart');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toMatch(/^Bearer /);
  });

  it('clears the session and announces expiry on 401 with a token', async () => {
    tokenStore.set(makeJwt(nowSec() + 3600));
    vi.stubGlobal('fetch', vi.fn(() => respond(401, { code: 1006, message: 'Unauthenticated' })));
    const expired = vi.fn();
    window.addEventListener('auth:expired', expired);
    await api('GET', '/cart').catch(() => {});
    window.removeEventListener('auth:expired', expired);
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it('a failed login (401, auth:false) does not end an existing session', async () => {
    tokenStore.set(makeJwt(nowSec() + 3600));
    vi.stubGlobal('fetch', vi.fn(() => respond(401, { code: 1006, message: 'Unauthenticated' })));
    await api('POST', '/auth/token', { username: 'a', password: 'b' }, { auth: false }).catch(() => {});
    expect(localStorage.getItem(TOKEN_KEY)).not.toBeNull();
  });

  it('refreshes a token that is about to expire, once, even with concurrent calls', async () => {
    tokenStore.set(makeJwt(nowSec() + 60));
    const newToken = makeJwt(nowSec() + 3600);
    const fetchMock = vi.fn((url) =>
      url.endsWith('/auth/refresh')
        ? respond(200, { code: 1000, result: { token: newToken, authenticated: true } })
        : respond(200, { code: 1000, result: 'ok' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await Promise.all([api('GET', '/cart'), api('GET', '/orders')]);

    const refreshCalls = fetchMock.mock.calls.filter(([u]) => u.endsWith('/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);
    expect(localStorage.getItem(TOKEN_KEY)).toBe(newToken);
    const apiCalls = fetchMock.mock.calls.filter(([u]) => !u.endsWith('/auth/refresh'));
    apiCalls.forEach(([, init]) => expect(init.headers.Authorization).toBe(`Bearer ${newToken}`));
  });

  it('secondsLeft is 0 for garbage tokens', () => {
    expect(secondsLeft('not-a-jwt')).toBe(0);
    expect(secondsLeft(null)).toBe(0);
  });
});
```

- [ ] **Step 4: Run to verify failure**

Run (in `shop-ui/`): `npm test`
Expected: FAIL, the modules do not exist.

- [ ] **Step 5: Implement the helpers**

`shop-ui/src/utils/money.js`:
```js
const formatter = new Intl.NumberFormat('vi-VN');

export function formatVnd(amount) {
  return `${formatter.format(amount ?? 0)} ₫`;
}
```

`shop-ui/src/utils/variants.js`:
```js
export function sizesOf(variants) {
  return [...new Set(variants.map((v) => v.size))];
}

export function colorsFor(variants, size) {
  return variants
    .filter((v) => v.size === size)
    .map((v) => ({ color: v.color, available: v.stock > 0 }));
}

export function findVariant(variants, size, color) {
  return variants.find((v) => v.size === size && v.color === color);
}
```

`shop-ui/src/utils/labels.js`:
```js
export const ORDER_STATUS_LABEL = {
  PENDING_PAYMENT: 'Chờ thanh toán',
  PENDING_CONFIRM: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  SHIPPING: 'Đang giao',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã huỷ',
};

export const PAYMENT_STATUS_LABEL = {
  UNPAID: 'Chưa thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  EXPIRED: 'Hết hạn thanh toán',
};

/** Mirrors OrderStatus.canTransitionTo on the server. */
export const NEXT_STATUSES = {
  PENDING_PAYMENT: ['CANCELLED'],
  PENDING_CONFIRM: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPING', 'CANCELLED'],
  SHIPPING: ['COMPLETED'],
  COMPLETED: [],
  CANCELLED: [],
};
```

`shop-ui/src/api/client.js`:
```js
const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8081/identity';

export const TOKEN_KEY = 'auth_token';
export const REFRESH_THRESHOLD_SECONDS = 300;

export class ApiError extends Error {
  constructor(message, code, status) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

export const tokenStore = {
  get() {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  },
  set(token) {
    try { localStorage.setItem(TOKEN_KEY, token); } catch { /* storage unavailable */ }
  },
  clear() {
    try { localStorage.removeItem(TOKEN_KEY); } catch { /* storage unavailable */ }
  },
};

export function decodeJwt(token) {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = part + '='.repeat((4 - (part.length % 4)) % 4);
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

export function secondsLeft(token, nowMs = Date.now()) {
  const payload = token ? decodeJwt(token) : null;
  if (!payload || !payload.exp) return 0;
  return Math.floor(payload.exp - nowMs / 1000);
}

async function send(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  let res;
  try {
    res = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError('Không kết nối được máy chủ', -1, 0);
  }
  if (res.status === 204) return null;
  let json = null;
  try { json = await res.json(); } catch { /* non-JSON body */ }
  if (!res.ok || !json || json.code !== 1000) {
    throw new ApiError(json?.message || `Lỗi ${res.status}`, json?.code ?? -1, res.status);
  }
  return json.result ?? null;
}

let refreshing = null;

async function ensureFreshToken() {
  const token = tokenStore.get();
  if (!token) return null;
  if (secondsLeft(token) > REFRESH_THRESHOLD_SECONDS) return token;
  if (!refreshing) {
    refreshing = send('POST', '/auth/refresh', { token })
      .then((result) => {
        tokenStore.set(result.token);
        window.dispatchEvent(new Event('auth:refreshed'));
        return result.token;
      })
      .catch(() => tokenStore.get())
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

export async function api(method, path, body, { auth = true } = {}) {
  const token = auth ? await ensureFreshToken() : null;
  try {
    return await send(method, path, body, token);
  } catch (e) {
    if (auth && token && e instanceof ApiError && e.status === 401) {
      tokenStore.clear();
      window.dispatchEvent(new Event('auth:expired'));
    }
    throw e;
  }
}
```

- [ ] **Step 6: Run to verify it passes**

Run (in `shop-ui/`): `npm test`
Expected: all tests PASS (money 2, variants 3, client 8).

- [ ] **Step 7: Commit**

```bash
git add shop-ui
git commit -m "feat(ui): scaffold Vite app, API client with proactive token refresh, helpers"
```

---

### Task 2: Auth, layout, routing, styling

**Files:**
- Create: `shop-ui/src/context/AuthContext.jsx`, `shop-ui/src/components/Header.jsx`, `shop-ui/src/components/ProtectedRoute.jsx`, `shop-ui/src/pages/Login.jsx`, `shop-ui/src/pages/Signup.jsx`
- Modify: `shop-ui/src/App.jsx`, `shop-ui/src/styles.css`

**Interfaces:**
- Consumes: `api`, `tokenStore`, `decodeJwt` (Task 1).
- Produces: `useAuth() -> { session: {username, isAdmin} | null, login(username, password), signup(fields), logout() }`; `<ProtectedRoute admin?>`; route table (pages from later tasks are stubbed until built).

- [ ] **Step 1: Create `AuthContext.jsx`**

```jsx
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, decodeJwt, secondsLeft, tokenStore } from '../api/client.js';

const AuthContext = createContext(null);

function sessionFrom(token) {
  if (!token || secondsLeft(token) <= 0) return null;
  const payload = decodeJwt(token);
  if (!payload) return null;
  const scopes = String(payload.scope || '').split(' ');
  return { username: payload.sub, isAdmin: scopes.includes('ROLE_ADMIN') };
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => tokenStore.get());

  useEffect(() => {
    const expired = () => setToken(null);
    const refreshed = () => setToken(tokenStore.get());
    window.addEventListener('auth:expired', expired);
    window.addEventListener('auth:refreshed', refreshed);
    return () => {
      window.removeEventListener('auth:expired', expired);
      window.removeEventListener('auth:refreshed', refreshed);
    };
  }, []);

  const login = useCallback(async (username, password) => {
    const result = await api('POST', '/auth/token', { username, password }, { auth: false });
    tokenStore.set(result.token);
    setToken(result.token);
  }, []);

  const signup = useCallback(async (fields) => {
    await api('POST', '/users', fields, { auth: false });
    await login(fields.username, fields.password);
  }, [login]);

  const logout = useCallback(async () => {
    const current = tokenStore.get();
    try {
      if (current) await api('POST', '/auth/logout', { token: current }, { auth: false });
    } catch { /* the token is dropped locally regardless */ }
    tokenStore.clear();
    setToken(null);
  }, []);

  const session = useMemo(() => sessionFrom(token), [token]);
  const value = useMemo(() => ({ session, login, signup, logout }), [session, login, signup, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
```

- [ ] **Step 2: Create `ProtectedRoute.jsx`**

```jsx
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export default function ProtectedRoute({ admin = false, children }) {
  const { session } = useAuth();
  const location = useLocation();
  if (!session) return <Navigate to="/login" replace state={{ from: location }} />;
  if (admin && !session.isAdmin) return <Navigate to="/" replace />;
  return children;
}
```

- [ ] **Step 3: Create `Header.jsx`** (cart count is wired in Task 3; use a prop-free placeholder now)

```jsx
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
```

- [ ] **Step 4: Create `Login.jsx` and `Signup.jsx`**

`Login.jsx`:
```jsx
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
```

`Signup.jsx`:
```jsx
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
```

- [ ] **Step 5: Replace `App.jsx`** with the full route table (pages added in later tasks are imported as they are created; until then, comment those routes out and uncomment as each task lands)

```jsx
import { Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext.jsx';
import Header from './components/Header.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import Login from './pages/Login.jsx';
import Signup from './pages/Signup.jsx';

export default function App() {
  return (
    <AuthProvider>
      <Header />
      <main className="container">
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/" element={<p>Trang chủ (Task 3)</p>} />
          <Route path="/admin/*" element={<ProtectedRoute admin><p>Quản trị (Task 5)</p></ProtectedRoute>} />
          <Route path="*" element={<p>Không tìm thấy trang.</p>} />
        </Routes>
      </main>
    </AuthProvider>
  );
}
```

- [ ] **Step 6: Replace `styles.css`**

```css
:root { --bg:#f6f7f9; --card:#fff; --text:#1c2430; --muted:#6b7686; --line:#e3e7ed; --brand:#1f3a5f; --accent:#c2410c; --ok:#15803d; --err:#b91c1c; }
* { box-sizing: border-box; }
body { margin:0; font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif; background:var(--bg); color:var(--text); }
a { color:var(--brand); text-decoration:none; }
a:hover { text-decoration:underline; }
.container { max-width:1080px; margin:0 auto; padding:16px; }
.header { background:var(--card); border-bottom:1px solid var(--line); position:sticky; top:0; z-index:10; }
.header-inner { display:flex; align-items:center; gap:24px; flex-wrap:wrap; }
.brand { font-weight:700; font-size:1.15rem; }
.nav { display:flex; gap:16px; align-items:center; }
.nav a.active { font-weight:600; border-bottom:2px solid var(--brand); }
.muted { color:var(--muted); }
.card { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:20px; margin:16px 0; }
.narrow { max-width:420px; margin:32px auto; }
.form { display:flex; flex-direction:column; gap:12px; }
.form label { display:flex; flex-direction:column; gap:4px; font-size:.9rem; }
input, select, textarea { padding:9px 10px; border:1px solid var(--line); border-radius:6px; font:inherit; background:#fff; }
.btn { padding:9px 16px; border:1px solid var(--line); border-radius:6px; background:#fff; cursor:pointer; font:inherit; }
.btn:disabled { opacity:.55; cursor:not-allowed; }
.btn-primary { background:var(--brand); color:#fff; border-color:var(--brand); }
.btn-danger { color:var(--err); border-color:var(--err); }
.btn-link { border:none; background:none; color:var(--brand); padding:0; }
.alert { padding:10px 12px; border-radius:6px; margin:0; }
.alert-error { background:#fee2e2; color:var(--err); }
.alert-ok { background:#dcfce7; color:var(--ok); }
.alert-info { background:#e0ecff; color:var(--brand); }
.grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(210px,1fr)); gap:16px; }
.product-card { background:var(--card); border:1px solid var(--line); border-radius:10px; overflow:hidden; display:flex; flex-direction:column; color:inherit; }
.product-card img, .img-placeholder { width:100%; aspect-ratio:3/4; object-fit:cover; background:#e9edf2; display:flex; align-items:center; justify-content:center; color:var(--muted); }
.product-card .body { padding:12px; }
.price { font-weight:700; color:var(--accent); }
.chips { display:flex; gap:8px; flex-wrap:wrap; margin:8px 0; }
.chip { padding:6px 12px; border:1px solid var(--line); border-radius:999px; background:#fff; cursor:pointer; }
.chip.on { background:var(--brand); color:#fff; border-color:var(--brand); }
.chip:disabled { opacity:.4; text-decoration:line-through; cursor:not-allowed; }
table { width:100%; border-collapse:collapse; }
th, td { text-align:left; padding:8px; border-bottom:1px solid var(--line); vertical-align:middle; }
.row { display:flex; gap:16px; flex-wrap:wrap; }
.row > * { flex:1 1 320px; }
.right { text-align:right; }
.badge { padding:2px 8px; border-radius:999px; background:#e9edf2; font-size:.8rem; }
.pager { display:flex; gap:12px; justify-content:center; align-items:center; margin:16px 0; }
@media (max-width:600px) { .header-inner { gap:12px; } }
```

- [ ] **Step 7: Verify in the browser**

Run (in `shop-ui/`): `npm run dev`, open `http://localhost:5173`. With the backend running:
- Sign up a new user: you are logged in and the header shows "Xin chào, <name>".
- Log out, then log in with a wrong password: the message "Sai tên đăng nhập hoặc mật khẩu" appears; with the right one you are logged in.
- Visit `/admin/products` as a normal user: redirected to `/`. Log in as `admin` (password = `ADMIN_PASSWORD` from `.env`): the "Quản trị" link appears and the placeholder shows.
Expected: all four behaviours hold; no CORS errors in the browser console.

- [ ] **Step 8: Run tests and commit**

Run: `npm test` (in `shop-ui/`). Expected: PASS.

```bash
git add shop-ui
git commit -m "feat(ui): auth context, login/signup, header, protected routes, styling"
```

---

### Task 3: Catalog and cart

**Files:**
- Create: `shop-ui/src/context/CartContext.jsx`, `shop-ui/src/components/ProductCard.jsx`, `shop-ui/src/pages/Home.jsx`, `shop-ui/src/pages/ProductDetail.jsx`, `shop-ui/src/pages/Cart.jsx`
- Modify: `shop-ui/src/App.jsx`, `shop-ui/src/components/Header.jsx` (already accepts `cartCount`)

**Interfaces:**
- Consumes: `api`, `useAuth`, `formatVnd`, `sizesOf/colorsFor/findVariant`.
- Produces: `useCart() -> { cart: {items, subtotal, totalQuantity}, add(variantId, qty), update(variantId, qty), remove(variantId), reload() }`; routes `/`, `/products/:slug`, `/cart`.

- [ ] **Step 1: Create `CartContext.jsx`**

```jsx
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client.js';
import { useAuth } from './AuthContext.jsx';

const EMPTY = { items: [], subtotal: 0, totalQuantity: 0 };
const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { session } = useAuth();
  const [cart, setCart] = useState(EMPTY);

  const reload = useCallback(async () => {
    if (!session) return setCart(EMPTY);
    try {
      setCart(await api('GET', '/cart'));
    } catch {
      setCart(EMPTY);
    }
  }, [session]);

  useEffect(() => { reload(); }, [reload]);

  const add = useCallback(async (variantId, quantity) => setCart(await api('POST', '/cart/items', { variantId, quantity })), []);
  const update = useCallback(async (variantId, quantity) => setCart(await api('PUT', `/cart/items/${variantId}`, { variantId, quantity })), []);
  const remove = useCallback(async (variantId) => setCart(await api('DELETE', `/cart/items/${variantId}`)), []);

  const value = useMemo(() => ({ cart, add, update, remove, reload }), [cart, add, update, remove, reload]);
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  return useContext(CartContext);
}
```

- [ ] **Step 2: Create `ProductCard.jsx`**

```jsx
import { Link } from 'react-router-dom';
import { formatVnd } from '../utils/money.js';

export default function ProductCard({ product }) {
  return (
    <Link to={`/products/${product.slug}`} className="product-card">
      {product.imageUrl
        ? <img src={product.imageUrl} alt={product.name} loading="lazy" />
        : <div className="img-placeholder">Chưa có ảnh</div>}
      <div className="body">
        <div>{product.name}</div>
        <div className="price">{formatVnd(product.basePrice)}</div>
        {product.categoryName && <div className="muted">{product.categoryName}</div>}
      </div>
    </Link>
  );
}
```

- [ ] **Step 3: Create `Home.jsx`** (filters live in the URL so pages are shareable and back-button friendly)

```jsx
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';
import ProductCard from '../components/ProductCard.jsx';

export default function Home() {
  const [params, setParams] = useSearchParams();
  const category = params.get('category') || '';
  const q = params.get('q') || '';
  const page = Number(params.get('page') || 0);

  const [categories, setCategories] = useState([]);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [search, setSearch] = useState(q);

  useEffect(() => {
    api('GET', '/categories', undefined, { auth: false }).then(setCategories).catch(() => {});
  }, []);

  useEffect(() => {
    setError('');
    const query = new URLSearchParams({ category, q, page: String(page), size: '12' });
    api('GET', `/products?${query}`, undefined, { auth: false })
      .then(setData)
      .catch((e) => setError(e.message));
  }, [category, q, page]);

  const update = (next) => setParams({ category, q, page: '0', ...next }, { replace: true });

  return (
    <>
      <div className="chips">
        <button className={`chip ${category === '' ? 'on' : ''}`} onClick={() => update({ category: '' })}>Tất cả</button>
        {categories.map((c) => (
          <button key={c.id} className={`chip ${category === c.slug ? 'on' : ''}`} onClick={() => update({ category: c.slug })}>{c.name}</button>
        ))}
      </div>
      <form onSubmit={(e) => { e.preventDefault(); update({ q: search.trim() }); }} className="row" style={{ marginBottom: 12 }}>
        <input placeholder="Tìm sản phẩm..." value={search} onChange={(e) => setSearch(e.target.value)} />
      </form>

      {error && <p className="alert alert-error">{error}</p>}
      {!data && !error && <p className="muted">Đang tải...</p>}
      {data && data.items.length === 0 && <p className="muted">Không có sản phẩm phù hợp.</p>}
      {data && (
        <>
          <div className="grid">{data.items.map((p) => <ProductCard key={p.id} product={p} />)}</div>
          {data.totalPages > 1 && (
            <div className="pager">
              <button className="btn" disabled={page <= 0} onClick={() => update({ page: String(page - 1) })}>Trước</button>
              <span>Trang {page + 1} / {data.totalPages}</span>
              <button className="btn" disabled={page + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })}>Sau</button>
            </div>
          )}
        </>
      )}
    </>
  );
}
```

- [ ] **Step 4: Create `ProductDetail.jsx`**

```jsx
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';
import { formatVnd } from '../utils/money.js';
import { colorsFor, findVariant, sizesOf } from '../utils/variants.js';

export default function ProductDetail() {
  const { slug } = useParams();
  const { session } = useAuth();
  const { add } = useCart();
  const navigate = useNavigate();
  const location = useLocation();

  const [product, setProduct] = useState(null);
  const [error, setError] = useState('');
  const [size, setSize] = useState('');
  const [color, setColor] = useState('');
  const [qty, setQty] = useState(1);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setProduct(null);
    setError('');
    api('GET', `/products/${slug}`, undefined, { auth: false }).then(setProduct).catch((e) => setError(e.message));
  }, [slug]);

  const variants = product?.variants ?? [];
  const sizes = useMemo(() => sizesOf(variants), [variants]);
  const colors = useMemo(() => (size ? colorsFor(variants, size) : []), [variants, size]);
  const variant = size && color ? findVariant(variants, size, color) : undefined;

  async function addToCart() {
    setMessage('');
    if (!session) return navigate('/login', { state: { from: location } });
    setBusy(true);
    try {
      await add(variant.id, qty);
      setMessage('Đã thêm vào giỏ hàng');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  if (error && !product) return <p className="alert alert-error">{error}</p>;
  if (!product) return <p className="muted">Đang tải...</p>;

  const price = variant ? variant.price : product.basePrice;
  return (
    <div className="card row">
      <div>
        {product.imageUrl ? <img src={product.imageUrl} alt={product.name} style={{ width: '100%', borderRadius: 8 }} /> : <div className="img-placeholder">Chưa có ảnh</div>}
      </div>
      <div>
        <h1>{product.name}</h1>
        <p className="price" style={{ fontSize: '1.4rem' }}>{formatVnd(price)}</p>
        {product.description && <p>{product.description}</p>}

        <strong>Size</strong>
        <div className="chips">
          {sizes.map((s) => (
            <button key={s} className={`chip ${size === s ? 'on' : ''}`} onClick={() => { setSize(s); setColor(''); }}>{s}</button>
          ))}
        </div>

        <strong>Màu</strong>
        <div className="chips">
          {!size && <span className="muted">Chọn size trước</span>}
          {colors.map((c) => (
            <button key={c.color} disabled={!c.available} className={`chip ${color === c.color ? 'on' : ''}`} onClick={() => setColor(c.color)}>{c.color}</button>
          ))}
        </div>

        {variant && <p className="muted">Còn {variant.stock} sản phẩm</p>}
        <label className="form" style={{ maxWidth: 120 }}>Số lượng
          <input type="number" min="1" max={variant ? Math.min(variant.stock, 99) : 99} value={qty}
            onChange={(e) => setQty(Math.max(1, Number(e.target.value) || 1))} />
        </label>

        {error && <p className="alert alert-error">{error}</p>}
        {message && <p className="alert alert-ok">{message}</p>}
        <p><button className="btn btn-primary" disabled={!variant || busy} onClick={addToCart}>Thêm vào giỏ</button></p>
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Create `Cart.jsx`**

```jsx
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext.jsx';
import { formatVnd } from '../utils/money.js';

export default function Cart() {
  const { cart, update, remove } = useCart();
  const navigate = useNavigate();
  const [error, setError] = useState('');

  const run = async (fn) => {
    setError('');
    try { await fn(); } catch (e) { setError(e.message); }
  };
  const hasUnavailable = cart.items.some((i) => !i.available);

  if (cart.items.length === 0) {
    return <div className="card"><p>Giỏ hàng trống.</p><Link to="/">Tiếp tục mua sắm</Link></div>;
  }
  return (
    <div className="card">
      <h1>Giỏ hàng</h1>
      {error && <p className="alert alert-error">{error}</p>}
      {hasUnavailable && <p className="alert alert-error">Một số sản phẩm đã hết hàng hoặc không đủ số lượng. Hãy chỉnh lại trước khi thanh toán.</p>}
      <table>
        <thead><tr><th>Sản phẩm</th><th>Đơn giá</th><th>SL</th><th className="right">Thành tiền</th><th /></tr></thead>
        <tbody>
          {cart.items.map((i) => (
            <tr key={i.variantId}>
              <td><Link to={`/products/${i.productSlug}`}>{i.productName}</Link><div className="muted">{i.size} / {i.color}{!i.available && ' · không đủ hàng'}</div></td>
              <td>{formatVnd(i.unitPrice)}</td>
              <td>
                <input type="number" min="1" max="99" defaultValue={i.quantity} style={{ width: 70 }}
                  onBlur={(e) => { const q = Number(e.target.value); if (q >= 1 && q !== i.quantity) run(() => update(i.variantId, q)); }} />
              </td>
              <td className="right">{formatVnd(i.lineTotal)}</td>
              <td><button className="btn btn-danger" onClick={() => run(() => remove(i.variantId))}>Xoá</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="right"><strong>Tạm tính: {formatVnd(cart.subtotal)}</strong></p>
      <p className="right"><button className="btn btn-primary" disabled={hasUnavailable} onClick={() => navigate('/checkout')}>Thanh toán</button></p>
    </div>
  );
}
```

- [ ] **Step 6: Wire routes, provider, and header count.** In `App.jsx` add imports for `CartProvider`, `useCart`, `Home`, `ProductDetail`, `Cart`; wrap children in `<CartProvider>` inside `<AuthProvider>`; make a small `Shell` component so the header can read the cart:

```jsx
import { Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext.jsx';
import { CartProvider, useCart } from './context/CartContext.jsx';
import Header from './components/Header.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import Login from './pages/Login.jsx';
import Signup from './pages/Signup.jsx';
import Home from './pages/Home.jsx';
import ProductDetail from './pages/ProductDetail.jsx';
import Cart from './pages/Cart.jsx';

function Shell() {
  const { cart } = useCart();
  return (
    <>
      <Header cartCount={cart.totalQuantity} />
      <main className="container">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/products/:slug" element={<ProductDetail />} />
          <Route path="/cart" element={<ProtectedRoute><Cart /></ProtectedRoute>} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/admin/*" element={<ProtectedRoute admin><p>Quản trị (Task 5)</p></ProtectedRoute>} />
          <Route path="*" element={<p>Không tìm thấy trang.</p>} />
        </Routes>
      </main>
    </>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <Shell />
      </CartProvider>
    </AuthProvider>
  );
}
```

- [ ] **Step 7: Verify in the browser**

First create a product as admin through the API or Postman (the admin UI arrives in Task 5): category, product with two variants (one stock 0), image URL any public image. Then:
- Home lists it; category chip and search narrow the list.
- Product page: choose size, the out-of-stock colour is disabled; while logged out, "Thêm vào giỏ" sends you to login and returns to the product afterwards.
- Logged in: add to cart, header shows the count, cart page edits quantity and removes lines; quantity above stock shows the server's "Not enough stock" message.
Expected: all hold with no console errors.

- [ ] **Step 8: Commit**

```bash
git add shop-ui
git commit -m "feat(ui): product browse/search, detail with size and colour, cart"
```

---

### Task 4: Checkout, MoMo return, orders

**Files:**
- Create: `shop-ui/src/pages/Checkout.jsx`, `shop-ui/src/pages/PaymentResult.jsx`, `shop-ui/src/pages/Orders.jsx`, `shop-ui/src/pages/OrderDetail.jsx`
- Modify: `shop-ui/src/App.jsx`

**Interfaces:**
- Consumes: `api`, `useCart`, `formatVnd`, `ORDER_STATUS_LABEL`, `PAYMENT_STATUS_LABEL`; backend `GET /shipping/provinces`, `POST /orders`, `POST /orders/{code}/pay/momo`, `GET /payments/momo/return?orderCode=`, `GET /orders`, `GET /orders/{code}`.
- Produces: routes `/checkout`, `/payment/result`, `/orders`, `/orders/:code`.

- [ ] **Step 1: Create `Checkout.jsx`**

```jsx
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client.js';
import { useCart } from '../context/CartContext.jsx';
import { formatVnd } from '../utils/money.js';

const PHONE = /^(0|\+84)[0-9]{9}$/;

export default function Checkout() {
  const { cart, reload } = useCart();
  const navigate = useNavigate();
  const [provinces, setProvinces] = useState([]);
  const [form, setForm] = useState({ receiverName: '', phone: '', email: '', address: '', province: '', note: '', paymentMethod: 'MOMO' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  useEffect(() => {
    api('GET', '/shipping/provinces', undefined, { auth: false }).then(setProvinces).catch((e) => setError(e.message));
  }, []);

  const shippingFee = useMemo(() => provinces.find((p) => p.province === form.province)?.fee ?? 0, [provinces, form.province]);
  const hasUnavailable = cart.items.some((i) => !i.available);

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (!PHONE.test(form.phone.trim())) return setError('Số điện thoại không hợp lệ (ví dụ 0901234567)');
    if (!form.province) return setError('Hãy chọn tỉnh/thành');
    setBusy(true);
    let order;
    try {
      order = await api('POST', '/orders', { ...form, note: form.note.trim() || null });
    } catch (err) {
      setBusy(false);
      return setError(err.message);
    }
    await reload(); // the server emptied the cart
    if (form.paymentMethod === 'COD') return navigate(`/orders/${order.code}`, { replace: true, state: { placed: true } });
    try {
      const { payUrl } = await api('POST', `/orders/${order.code}/pay/momo`);
      window.location.assign(payUrl);
    } catch (err) {
      // The order exists; let the customer retry from its page.
      navigate(`/orders/${order.code}`, { replace: true, state: { payError: err.message } });
    }
  }

  if (cart.items.length === 0) return <div className="card"><p>Giỏ hàng trống.</p><Link to="/">Tiếp tục mua sắm</Link></div>;

  return (
    <form className="row" onSubmit={submit}>
      <div className="card form">
        <h1>Thông tin giao hàng</h1>
        {error && <p className="alert alert-error">{error}</p>}
        {hasUnavailable && <p className="alert alert-error">Giỏ hàng có sản phẩm không đủ hàng. <Link to="/cart">Chỉnh giỏ hàng</Link></p>}
        <label>Họ tên người nhận<input value={form.receiverName} onChange={set('receiverName')} required maxLength={100} /></label>
        <label>Số điện thoại<input value={form.phone} onChange={set('phone')} required inputMode="tel" /></label>
        <label>Email nhận xác nhận đơn<input type="email" value={form.email} onChange={set('email')} required /></label>
        <label>Tỉnh/Thành
          <select value={form.province} onChange={set('province')} required>
            <option value="">-- Chọn --</option>
            {provinces.map((p) => <option key={p.id} value={p.province}>{p.province}</option>)}
          </select>
        </label>
        <label>Địa chỉ<input value={form.address} onChange={set('address')} required maxLength={300} /></label>
        <label>Ghi chú<textarea value={form.note} onChange={set('note')} maxLength={500} rows={2} /></label>
      </div>

      <div className="card form">
        <h2>Đơn hàng</h2>
        {cart.items.map((i) => (
          <div key={i.variantId} className="row" style={{ gap: 8 }}>
            <span>{i.productName} ({i.size}/{i.color}) × {i.quantity}</span>
            <span className="right">{formatVnd(i.lineTotal)}</span>
          </div>
        ))}
        <hr />
        <div className="row"><span>Tạm tính</span><span className="right">{formatVnd(cart.subtotal)}</span></div>
        <div className="row"><span>Phí vận chuyển</span><span className="right">{form.province ? formatVnd(shippingFee) : '—'}</span></div>
        <div className="row"><strong>Tổng cộng</strong><strong className="right price">{formatVnd(cart.subtotal + shippingFee)}</strong></div>

        <h3>Phương thức thanh toán</h3>
        <label><input type="radio" name="pm" checked={form.paymentMethod === 'MOMO'} onChange={() => setForm({ ...form, paymentMethod: 'MOMO' })} /> MoMo (ví, thẻ ATM, thẻ Visa/Mastercard)</label>
        <label><input type="radio" name="pm" checked={form.paymentMethod === 'COD'} onChange={() => setForm({ ...form, paymentMethod: 'COD' })} /> Thanh toán khi nhận hàng (COD)</label>
        <button className="btn btn-primary" disabled={busy || hasUnavailable}>{busy ? 'Đang xử lý...' : form.paymentMethod === 'MOMO' ? 'Thanh toán với MoMo' : 'Đặt hàng'}</button>
        <p className="muted">Giá cuối cùng được máy chủ tính lại khi đặt hàng.</p>
      </div>
    </form>
  );
}
```

- [ ] **Step 2: Create `PaymentResult.jsx`** (safe to run repeatedly)

```jsx
import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';

export default function PaymentResult() {
  const [params] = useSearchParams();
  const orderCode = params.get('orderCode');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const check = useCallback(async () => {
    if (!orderCode) return setError('Thiếu mã đơn hàng.');
    setBusy(true);
    setError('');
    try {
      setResult(await api('GET', `/payments/momo/return?orderCode=${encodeURIComponent(orderCode)}`));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }, [orderCode]);

  useEffect(() => { check(); }, [check]);

  return (
    <div className="card narrow">
      <h1>Kết quả thanh toán</h1>
      {busy && !result && <p className="muted">Đang xác nhận với MoMo...</p>}
      {error && <p className="alert alert-error">{error}</p>}
      {result?.paymentStatus === 'PAID' && <p className="alert alert-ok">Thanh toán thành công. Cảm ơn bạn! Email xác nhận sẽ được gửi tới bạn.</p>}
      {result && result.paymentStatus !== 'PAID' && result.orderStatus === 'PENDING_PAYMENT' && (
        <p className="alert alert-info">Chưa nhận được xác nhận thanh toán. Nếu bạn đã thanh toán, hãy bấm "Kiểm tra lại" sau vài giây.</p>
      )}
      {result && result.orderStatus === 'CANCELLED' && <p className="alert alert-error">Đơn hàng đã bị huỷ (hết hạn thanh toán).</p>}
      <p>
        <button className="btn" onClick={check} disabled={busy}>Kiểm tra lại</button>{' '}
        {orderCode && <Link to={`/orders/${orderCode}`}>Xem đơn hàng</Link>}
      </p>
    </div>
  );
}
```

- [ ] **Step 3: Create `Orders.jsx`**

```jsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { formatVnd } from '../utils/money.js';
import { ORDER_STATUS_LABEL, PAYMENT_STATUS_LABEL } from '../utils/labels.js';

export default function Orders() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api('GET', `/orders?page=${page}&size=10`).then(setData).catch((e) => setError(e.message));
  }, [page]);

  if (error) return <p className="alert alert-error">{error}</p>;
  if (!data) return <p className="muted">Đang tải...</p>;
  return (
    <div className="card">
      <h1>Đơn hàng của tôi</h1>
      {data.items.length === 0 && <p className="muted">Bạn chưa có đơn hàng nào.</p>}
      <table>
        <tbody>
          {data.items.map((o) => (
            <tr key={o.code}>
              <td><Link to={`/orders/${o.code}`}>{o.code}</Link><div className="muted">{new Date(o.createdAt).toLocaleString('vi-VN')}</div></td>
              <td><span className="badge">{ORDER_STATUS_LABEL[o.status]}</span> <span className="badge">{PAYMENT_STATUS_LABEL[o.paymentStatus]}</span></td>
              <td className="right">{formatVnd(o.total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.totalPages > 1 && (
        <div className="pager">
          <button className="btn" disabled={page <= 0} onClick={() => setPage(page - 1)}>Trước</button>
          <span>Trang {page + 1} / {data.totalPages}</span>
          <button className="btn" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Sau</button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Create `OrderDetail.jsx`** (retry payment while the window is open)

```jsx
import { useEffect, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { formatVnd } from '../utils/money.js';
import { ORDER_STATUS_LABEL, PAYMENT_STATUS_LABEL } from '../utils/labels.js';

export default function OrderDetail() {
  const { code } = useParams();
  const location = useLocation();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(location.state?.payError || '');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api('GET', `/orders/${code}`).then(setOrder).catch((e) => setError(e.message));
  }, [code]);

  async function payWithMomo() {
    setBusy(true);
    setError('');
    try {
      const { payUrl } = await api('POST', `/orders/${code}/pay/momo`);
      window.location.assign(payUrl);
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }

  if (!order) return error ? <p className="alert alert-error">{error}</p> : <p className="muted">Đang tải...</p>;
  const payable = order.status === 'PENDING_PAYMENT' && order.paymentMethod === 'MOMO'
    && order.expiresAt && new Date(order.expiresAt) > new Date();

  return (
    <div className="card">
      <h1>Đơn hàng {order.code}</h1>
      {location.state?.placed && <p className="alert alert-ok">Đặt hàng thành công. Chúng tôi đã gửi email xác nhận.</p>}
      {error && <p className="alert alert-error">{error}</p>}
      <p><span className="badge">{ORDER_STATUS_LABEL[order.status]}</span> <span className="badge">{PAYMENT_STATUS_LABEL[order.paymentStatus]}</span> <span className="badge">{order.paymentMethod}</span></p>
      {payable && (
        <p className="alert alert-info">
          Đơn hàng chờ thanh toán đến {new Date(order.expiresAt).toLocaleTimeString('vi-VN')}.{' '}
          <button className="btn btn-primary" disabled={busy} onClick={payWithMomo}>Thanh toán với MoMo</button>
        </p>
      )}
      <table>
        <thead><tr><th>Sản phẩm</th><th>SL</th><th className="right">Thành tiền</th></tr></thead>
        <tbody>
          {order.items.map((i, idx) => (
            <tr key={idx}><td>{i.productName}<div className="muted">{i.size} / {i.color}</div></td><td>{i.quantity}</td><td className="right">{formatVnd(i.lineTotal)}</td></tr>
          ))}
        </tbody>
      </table>
      <p className="right">Tạm tính: {formatVnd(order.subtotal)}<br />Phí vận chuyển: {formatVnd(order.shippingFee)}<br /><strong>Tổng cộng: {formatVnd(order.total)}</strong></p>
      <p>Giao đến: {order.receiverName}, {order.phone}<br />{order.address}, {order.province}</p>
    </div>
  );
}
```

- [ ] **Step 5: Add the routes** in `App.jsx` (imports for the four pages) inside `<Routes>`:

```jsx
          <Route path="/checkout" element={<ProtectedRoute><Checkout /></ProtectedRoute>} />
          <Route path="/payment/result" element={<ProtectedRoute><PaymentResult /></ProtectedRoute>} />
          <Route path="/orders" element={<ProtectedRoute><Orders /></ProtectedRoute>} />
          <Route path="/orders/:code" element={<ProtectedRoute><OrderDetail /></ProtectedRoute>} />
```

- [ ] **Step 6: Verify the purchase flows in the browser** (backend running with MoMo sandbox keys and mail settings from Plan 3, Task 13)

- COD: add to cart, checkout with COD; you land on the order page with the success banner; the mail inbox receives the confirmation; stock dropped by the quantity; cart is empty.
- MoMo: checkout with MoMo; you are redirected to the MoMo sandbox page; pay with the sandbox test wallet/card; MoMo returns you to `/payment/result?orderCode=...` which shows "Thanh toán thành công"; refreshing the page still shows success (idempotent); the order is `Chờ xác nhận / Đã thanh toán`; email received once.
- Abandon a MoMo payment (close the MoMo tab): open the order from "Đơn hàng", press "Thanh toán với MoMo" and pay successfully.
- Stop the backend's MoMo keys (blank `MOMO_ACCESS_KEY`), restart, choose MoMo at checkout: the order is created, you land on its page with the error message and can retry after fixing the keys.
Expected: all four behave as described.

- [ ] **Step 7: Commit**

```bash
git add shop-ui
git commit -m "feat(ui): checkout with COD/MoMo, payment result, order list and detail"
```

---

### Task 5: Admin area

**Files:**
- Create: `shop-ui/src/pages/admin/AdminLayout.jsx`, `shop-ui/src/pages/admin/AdminProducts.jsx`, `shop-ui/src/pages/admin/AdminOrders.jsx`, `shop-ui/src/pages/admin/AdminShipping.jsx`
- Modify: `shop-ui/src/App.jsx`

**Interfaces:**
- Consumes: backend admin endpoints from Plans 2a and 2b; `NEXT_STATUSES`, `ORDER_STATUS_LABEL`.
- Produces: routes `/admin/products`, `/admin/orders`, `/admin/shipping` (all behind `<ProtectedRoute admin>`).

- [ ] **Step 1: Create `AdminLayout.jsx`**

```jsx
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
```

- [ ] **Step 2: Create `AdminShipping.jsx`**

```jsx
import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';

export default function AdminShipping() {
  const [rates, setRates] = useState([]);
  const [fees, setFees] = useState({});
  const [newRate, setNewRate] = useState({ province: '', fee: 35000 });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const load = () => api('GET', '/admin/shipping-rates').then((list) => {
    setRates(list);
    setFees(Object.fromEntries(list.map((r) => [r.id, r.fee])));
  }).catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);

  async function save(rate) {
    setError(''); setMessage('');
    try {
      await api('PUT', `/admin/shipping-rates/${rate.id}`, { province: rate.province, fee: Number(fees[rate.id]) });
      setMessage(`Đã lưu ${rate.province}`);
    } catch (e) { setError(e.message); }
  }

  async function create(e) {
    e.preventDefault();
    setError(''); setMessage('');
    try {
      await api('POST', '/admin/shipping-rates', { province: newRate.province.trim(), fee: Number(newRate.fee) });
      setNewRate({ province: '', fee: 35000 });
      await load();
    } catch (err) { setError(err.message); }
  }

  return (
    <div className="card">
      <h1>Phí vận chuyển theo tỉnh/thành</h1>
      {error && <p className="alert alert-error">{error}</p>}
      {message && <p className="alert alert-ok">{message}</p>}
      <form className="row" onSubmit={create}>
        <input placeholder="Tỉnh/Thành mới" value={newRate.province} onChange={(e) => setNewRate({ ...newRate, province: e.target.value })} required />
        <input type="number" min="0" value={newRate.fee} onChange={(e) => setNewRate({ ...newRate, fee: e.target.value })} required />
        <button className="btn btn-primary">Thêm</button>
      </form>
      <table>
        <thead><tr><th>Tỉnh/Thành</th><th>Phí (₫)</th><th /></tr></thead>
        <tbody>
          {rates.map((r) => (
            <tr key={r.id}>
              <td>{r.province}</td>
              <td><input type="number" min="0" value={fees[r.id] ?? ''} onChange={(e) => setFees({ ...fees, [r.id]: e.target.value })} /></td>
              <td><button className="btn" onClick={() => save(r)}>Lưu</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 3: Create `AdminOrders.jsx`**

```jsx
import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import { formatVnd } from '../../utils/money.js';
import { NEXT_STATUSES, ORDER_STATUS_LABEL, PAYMENT_STATUS_LABEL } from '../../utils/labels.js';

export default function AdminOrders() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const load = () => {
    const query = new URLSearchParams({ page: String(page), size: '20' });
    if (status) query.set('status', status);
    return api('GET', `/admin/orders?${query}`).then(setData).catch((e) => setError(e.message));
  };
  useEffect(() => { load(); }, [status, page]); // eslint-disable-line react-hooks/exhaustive-deps

  async function change(code, next) {
    setError('');
    if (next === 'CANCELLED' && !window.confirm(`Huỷ đơn ${code}? Kho sẽ được hoàn lại.`)) return;
    try {
      await api('PUT', `/admin/orders/${code}/status`, { status: next });
      await load();
    } catch (e) { setError(e.message); }
  }

  return (
    <div className="card">
      <h1>Đơn hàng</h1>
      {error && <p className="alert alert-error">{error}</p>}
      <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
        <option value="">Tất cả trạng thái</option>
        {Object.entries(ORDER_STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </select>
      {!data ? <p className="muted">Đang tải...</p> : (
        <table>
          <thead><tr><th>Mã</th><th>Khách</th><th>Trạng thái</th><th className="right">Tổng</th><th>Thao tác</th></tr></thead>
          <tbody>
            {data.items.map((o) => (
              <tr key={o.code}>
                <td>{o.code}<div className="muted">{new Date(o.createdAt).toLocaleString('vi-VN')}</div></td>
                <td>{o.receiverName}<div className="muted">{o.phone} · {o.province}</div></td>
                <td><span className="badge">{ORDER_STATUS_LABEL[o.status]}</span> <span className="badge">{PAYMENT_STATUS_LABEL[o.paymentStatus]}</span> <span className="badge">{o.paymentMethod}</span></td>
                <td className="right">{formatVnd(o.total)}</td>
                <td>{NEXT_STATUSES[o.status].map((n) => (
                  <button key={n} className={`btn ${n === 'CANCELLED' ? 'btn-danger' : ''}`} onClick={() => change(o.code, n)}>{ORDER_STATUS_LABEL[n]}</button>
                ))}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && data.totalPages > 1 && (
        <div className="pager">
          <button className="btn" disabled={page <= 0} onClick={() => setPage(page - 1)}>Trước</button>
          <span>Trang {page + 1} / {data.totalPages}</span>
          <button className="btn" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Sau</button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Create `AdminProducts.jsx`** (list, create/edit product, variants, categories)

```jsx
import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import { formatVnd } from '../../utils/money.js';

const EMPTY_PRODUCT = { name: '', slug: '', description: '', categoryId: '', basePrice: 0, imageUrl: '', active: true };
const EMPTY_VARIANT = { size: '', color: '', sku: '', stock: 0, price: '' };

const slugify = (s) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd')
  .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

export default function AdminProducts() {
  const [list, setList] = useState(null);
  const [categories, setCategories] = useState([]);
  const [editing, setEditing] = useState(null);     // product detail or {new: true, ...EMPTY_PRODUCT}
  const [variant, setVariant] = useState(EMPTY_VARIANT);
  const [newCategory, setNewCategory] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const guard = async (fn, okMessage) => {
    setError(''); setMessage('');
    try { await fn(); if (okMessage) setMessage(okMessage); } catch (e) { setError(e.message); }
  };
  const loadList = () => api('GET', '/admin/products?size=100').then(setList);
  const loadCategories = () => api('GET', '/categories', undefined, { auth: false }).then(setCategories);
  useEffect(() => { guard(async () => { await Promise.all([loadList(), loadCategories()]); }); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const open = (id) => guard(async () => setEditing(await api('GET', `/admin/products/${id}`)));
  const productBody = (p) => ({
    name: p.name, slug: p.slug, description: p.description || '', categoryId: p.categoryId || p.category?.id || '',
    basePrice: Number(p.basePrice), imageUrl: p.imageUrl || '', active: p.active,
  });

  const saveProduct = (e) => {
    e.preventDefault();
    return guard(async () => {
      const body = productBody(editing);
      const saved = editing.isNew
        ? await api('POST', '/admin/products', body)
        : await api('PUT', `/admin/products/${editing.id}`, body);
      setEditing(saved);
      await loadList();
    }, 'Đã lưu sản phẩm');
  };

  const deactivate = (id) => window.confirm('Ẩn sản phẩm này khỏi cửa hàng?') && guard(async () => {
    await api('DELETE', `/admin/products/${id}`);
    setEditing(null);
    await loadList();
  }, 'Đã ẩn sản phẩm');

  const addVariant = (e) => {
    e.preventDefault();
    return guard(async () => {
      await api('POST', `/admin/products/${editing.id}/variants`, {
        ...variant, stock: Number(variant.stock), price: variant.price === '' ? null : Number(variant.price),
      });
      setVariant(EMPTY_VARIANT);
      setEditing(await api('GET', `/admin/products/${editing.id}`));
    }, 'Đã thêm biến thể');
  };

  const saveVariant = (v) => guard(async () => {
    await api('PUT', `/admin/variants/${v.id}`, {
      size: v.size, color: v.color, sku: v.sku, stock: Number(v.stock), price: v.priceOverride ? Number(v.priceOverride) : null, active: v.active,
    });
    setEditing(await api('GET', `/admin/products/${editing.id}`));
  }, 'Đã lưu biến thể');

  const createCategory = (e) => {
    e.preventDefault();
    return guard(async () => {
      await api('POST', '/admin/categories', { name: newCategory.trim(), slug: slugify(newCategory) });
      setNewCategory('');
      await loadCategories();
    }, 'Đã thêm danh mục');
  };

  const setField = (key) => (e) => setEditing({ ...editing, [key]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const patchVariant = (id, patch) => setEditing({ ...editing, variants: editing.variants.map((v) => (v.id === id ? { ...v, ...patch } : v)) });

  return (
    <div className="row">
      <div className="card">
        <h1>Sản phẩm</h1>
        {error && <p className="alert alert-error">{error}</p>}
        {message && <p className="alert alert-ok">{message}</p>}
        <p><button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY_PRODUCT, isNew: true, variants: [] })}>+ Thêm sản phẩm</button></p>
        <form className="row" onSubmit={createCategory}>
          <input placeholder="Danh mục mới" value={newCategory} onChange={(e) => setNewCategory(e.target.value)} required />
          <button className="btn">Thêm danh mục</button>
        </form>
        {!list ? <p className="muted">Đang tải...</p> : (
          <table>
            <tbody>
              {list.items.map((p) => (
                <tr key={p.id} onClick={() => open(p.id)} style={{ cursor: 'pointer' }}>
                  <td>{p.name}<div className="muted">{p.slug}</div></td>
                  <td>{formatVnd(p.basePrice)}</td>
                  <td>{p.active ? <span className="badge">Đang bán</span> : <span className="badge">Đã ẩn</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {editing && (
        <div className="card">
          <h2>{editing.isNew ? 'Sản phẩm mới' : 'Sửa sản phẩm'}</h2>
          <form className="form" onSubmit={saveProduct}>
            <label>Tên<input value={editing.name} required onChange={(e) => setEditing({ ...editing, name: e.target.value, slug: editing.isNew ? slugify(e.target.value) : editing.slug })} /></label>
            <label>Slug<input value={editing.slug} required pattern="[a-z0-9]+(-[a-z0-9]+)*" onChange={setField('slug')} /></label>
            <label>Danh mục
              <select value={editing.categoryId ?? editing.category?.id ?? ''} onChange={setField('categoryId')}>
                <option value="">(không)</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
            <label>Giá gốc (₫)<input type="number" min="0" required value={editing.basePrice} onChange={setField('basePrice')} /></label>
            <label>Link ảnh<input value={editing.imageUrl || ''} onChange={setField('imageUrl')} /></label>
            <label>Mô tả<textarea rows={3} value={editing.description || ''} onChange={setField('description')} /></label>
            <label><input type="checkbox" checked={editing.active} onChange={setField('active')} /> Đang bán</label>
            <div>
              <button className="btn btn-primary">Lưu</button>{' '}
              {!editing.isNew && editing.active && <button type="button" className="btn btn-danger" onClick={() => deactivate(editing.id)}>Ẩn sản phẩm</button>}
            </div>
          </form>

          {!editing.isNew && (
            <>
              <h3>Biến thể (size / màu / kho)</h3>
              <table>
                <thead><tr><th>Size</th><th>Màu</th><th>SKU</th><th>Kho</th><th>Giá riêng</th><th>Bán</th><th /></tr></thead>
                <tbody>
                  {editing.variants.map((v) => (
                    <tr key={v.id}>
                      <td>{v.size}</td><td>{v.color}</td><td>{v.sku}</td>
                      <td><input type="number" min="0" style={{ width: 70 }} value={v.stock} onChange={(e) => patchVariant(v.id, { stock: e.target.value })} /></td>
                      <td><input type="number" min="0" style={{ width: 100 }} placeholder="theo giá gốc" value={v.priceOverride ?? (v.price !== editing.basePrice ? v.price : '')} onChange={(e) => patchVariant(v.id, { priceOverride: e.target.value })} /></td>
                      <td><input type="checkbox" checked={v.active} onChange={(e) => patchVariant(v.id, { active: e.target.checked })} /></td>
                      <td><button className="btn" onClick={() => saveVariant(v)}>Lưu</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <form className="row" onSubmit={addVariant}>
                <input placeholder="Size" required value={variant.size} onChange={(e) => setVariant({ ...variant, size: e.target.value })} />
                <input placeholder="Màu" required value={variant.color} onChange={(e) => setVariant({ ...variant, color: e.target.value })} />
                <input placeholder="SKU" required value={variant.sku} onChange={(e) => setVariant({ ...variant, sku: e.target.value })} />
                <input type="number" min="0" placeholder="Kho" required value={variant.stock} onChange={(e) => setVariant({ ...variant, stock: e.target.value })} />
                <input type="number" min="0" placeholder="Giá riêng (tuỳ chọn)" value={variant.price} onChange={(e) => setVariant({ ...variant, price: e.target.value })} />
                <button className="btn">+ Thêm biến thể</button>
              </form>
            </>
          )}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 5: Route to the admin area.** In `App.jsx` import `AdminLayout` and replace the admin placeholder route with:

```jsx
          <Route path="/admin/*" element={<ProtectedRoute admin><AdminLayout /></ProtectedRoute>} />
```

- [ ] **Step 6: Verify in the browser** (logged in as `admin`)

- Products: add a category, add a product (slug auto-fills from the name), add two variants with different stock and one with its own price, save a stock change, hide the product. The storefront reflects each change (hidden product disappears, price override shows on the product page).
- Orders: place a COD order as a customer; as admin move it Chờ xác nhận -> Đã xác nhận -> Đang giao -> Hoàn thành (COD becomes "Đã thanh toán"); cancelling another restores stock (check on the product page).
- Shipping: change Hà Nội's fee; the checkout summary for that province updates after a reload.
- Log in as a normal user: `/admin/products` redirects to `/`.
Expected: all hold.

- [ ] **Step 7: Commit**

```bash
git add shop-ui
git commit -m "feat(ui): admin products/variants, orders and shipping fees"
```

---

### Task 6: Final verification and docs

**Files:**
- Modify: `CLAUDE.md`, `UI_SETUP.md` (or add a note), `docs/superpowers/specs/2026-09-30-clothing-shop-design.md` (only if behaviour changed while building)

- [ ] **Step 1: Production build and tests**

Run (in `shop-ui/`): `npm test && npm run build`
Expected: tests PASS; build completes and writes `shop-ui/dist/` (git-ignored).

- [ ] **Step 2: Full end-to-end pass in a clean state**

With backend and `npm run dev` running: sign up a new customer -> browse -> add two variants to the cart -> checkout with MoMo -> pay in the MoMo sandbox -> land on the success page -> see the order under "Đơn hàng" -> confirm the email arrived -> as admin, confirm and complete the order. Then repeat with COD, and once with a deliberately abandoned MoMo payment: leave it and wait for the expiry (15 minutes, or temporarily set `shop.order-expiry-minutes: 1` and restart) and confirm the order becomes cancelled and stock returns.
Expected: every step behaves as in Task 4/5 verification; no red errors in the browser console or backend log other than expected validation messages.

- [ ] **Step 3: Browser console and layout check**

Load each page at desktop width and at a 375px phone width (browser dev tools). Expected: no horizontal scrolling on the storefront pages; tables in the admin area may scroll horizontally on a phone (acceptable for an admin tool).

- [ ] **Step 4: Update docs**

In `CLAUDE.md`, replace the "Frontend UI - Modern Login & Dashboard" section with a short "Frontend (shop-ui)" section: how to run (`cd shop-ui && npm install && npm run dev`, requires Node 20, port 5173), `VITE_API_BASE`, the pages, and that `login.html`, `signup.html`, `dashboard.html` are superseded by the React app (keep or delete at the owner's choice; do not delete without asking). Add a line in `UI_SETUP.md` pointing to the new section.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md UI_SETUP.md docs shop-ui
git commit -m "docs: document shop-ui and finish end-to-end verification"
```
