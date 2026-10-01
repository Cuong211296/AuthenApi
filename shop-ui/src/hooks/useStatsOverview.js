import { useEffect, useState } from 'react';
import { api } from '../api/client.js';

export const statsPath = ({ from, to, groupBy }) => `/admin/stats/overview?${new URLSearchParams({ from, to, groupBy })}`;

/**
 * Admin statistics overview for { from, to, groupBy } (the caller validates the range; pass `enabled: false` to skip).
 * Returns { data, loading, stale, error, retry }:
 *  - data: the last successful response (kept while a newer query loads, so the page can dim it instead of jumping);
 *  - loading: no data for the current query yet and no error (show skeletons when there is no data at all);
 *  - stale: `data` belongs to an earlier query while the current one loads;
 *  - error: message of the failed current query, retry() runs it again.
 * Responses of superseded queries are ignored; the effect depends only on the query values and the retry counter.
 */
export default function useStatsOverview({ from, to, groupBy, enabled = true }) {
  const [last, setLast] = useState(null); // { key, data }
  const [failure, setFailure] = useState(null); // { key, attempt, message }
  const [attempt, setAttempt] = useState(0);
  const key = `${from}|${to}|${groupBy}`;

  useEffect(() => {
    if (!enabled) return undefined;
    let ignore = false;
    api('GET', statsPath({ from, to, groupBy }))
      .then((data) => {
        if (ignore) return;
        setLast({ key, data });
        setFailure(null);
      })
      .catch((e) => {
        if (!ignore) setFailure({ key, attempt, message: e?.message || 'Không tải được số liệu' });
      });
    return () => { ignore = true; };
  }, [from, to, groupBy, enabled, attempt, key]);

  const error = failure && failure.key === key && failure.attempt === attempt ? failure.message : '';
  const fresh = last?.key === key;
  return {
    data: last?.data ?? null,
    stale: Boolean(last) && !fresh,
    loading: enabled && !fresh && !error,
    error: enabled ? error : '',
    retry: () => setAttempt((n) => n + 1),
  };
}
