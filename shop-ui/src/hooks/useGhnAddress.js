import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api/client.js';
import { sortByName } from '../utils/address.js';

const IDLE = { key: null, status: 'idle', items: [] };

/**
 * One lazily loaded list. `key` identifies the parent (null = parent not chosen yet -> idle); results are cached per
 * key for the lifetime of the hook, stale responses are ignored. Returns { status, items, retry }.
 */
function useLazyList(key, load) {
  const cache = useRef(new Map());
  const loadRef = useRef(load);
  loadRef.current = load;
  const [state, setState] = useState(IDLE);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (key == null) { setState(IDLE); return undefined; }
    const hit = cache.current.get(key);
    if (hit) { setState({ key, status: 'ready', items: hit }); return undefined; }
    let ignore = false;
    setState({ key, status: 'loading', items: [] });
    loadRef.current()
      .then((list) => {
        const items = sortByName(Array.isArray(list) ? list : []);
        cache.current.set(key, items);
        if (!ignore) setState({ key, status: 'ready', items });
      })
      .catch(() => { if (!ignore) setState({ key, status: 'error', items: [] }); });
    return () => { ignore = true; };
  }, [key, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  // A new key renders once with the previous key's state: treat that as loading instead of showing stale options.
  const current = key == null ? IDLE : state.key === key ? state : { key, status: 'loading', items: [] };
  return { status: current.status, items: current.items, retry };
}

/**
 * GHN master data for the dependent selects (province -> district -> ward) through the public proxy endpoints.
 * `enabled` gates the first request (only in GHN_IDS mode). Each list is { status: 'idle'|'loading'|'ready'|'error', items, retry }.
 */
export function useGhnAddress({ enabled, provinceId, districtId }) {
  const provinces = useLazyList(enabled ? 'all' : null, () => api('GET', '/shipping/ghn/provinces', undefined, { auth: false }));
  const districts = useLazyList(
    enabled && provinceId ? `p${provinceId}` : null,
    () => api('GET', `/shipping/ghn/districts?provinceId=${encodeURIComponent(provinceId)}`, undefined, { auth: false }),
  );
  const wards = useLazyList(
    enabled && provinceId && districtId ? `p${provinceId}d${districtId}` : null,
    () => api('GET', `/shipping/ghn/wards?provinceId=${encodeURIComponent(provinceId)}&districtId=${encodeURIComponent(districtId)}`, undefined, { auth: false }),
  );
  return { provinces, districts, wards };
}
