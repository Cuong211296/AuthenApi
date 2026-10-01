import { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { DEFAULT_CONFIG } from '../utils/address.js';

let cached = null; // session cache; failures are not cached so the next visit retries

/** `GET /shipping/config` once per session; falls back to the fixed-table text address when it cannot be read. */
export function useShippingConfig() {
  const [state, setState] = useState(() => (cached ? { config: cached, loading: false } : { config: DEFAULT_CONFIG, loading: true }));

  useEffect(() => {
    if (cached) return undefined;
    let ignore = false;
    api('GET', '/shipping/config', undefined, { auth: false })
      .then((config) => {
        if (!config || typeof config.addressMode !== 'string') throw new Error('bad config');
        cached = config;
        if (!ignore) setState({ config, loading: false });
      })
      .catch(() => { if (!ignore) setState({ config: DEFAULT_CONFIG, loading: false }); });
    return () => { ignore = true; };
  }, []);

  return state;
}
