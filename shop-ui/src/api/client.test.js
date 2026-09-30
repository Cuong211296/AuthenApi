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
