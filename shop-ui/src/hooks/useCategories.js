import { useEffect, useState } from 'react';
import { api } from '../api/client.js';

let cached = null;

/** Product categories (public endpoint), fetched once per page load and shared by the header and home page. */
export function useCategories() {
  const [categories, setCategories] = useState([]);
  useEffect(() => {
    let ignore = false;
    if (!cached) {
      cached = api('GET', '/categories', undefined, { auth: false }).catch((e) => { cached = null; throw e; });
    }
    cached.then((r) => { if (!ignore) setCategories(r); }).catch(() => {});
    return () => { ignore = true; };
  }, []);
  return categories;
}
