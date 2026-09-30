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
