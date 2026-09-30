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
    // Only end the session if the rejected token is still the stored one (a newer login must survive).
    if (auth && token && e instanceof ApiError && e.status === 401 && tokenStore.get() === token) {
      tokenStore.clear();
      window.dispatchEvent(new Event('auth:expired'));
    }
    throw e;
  }
}
