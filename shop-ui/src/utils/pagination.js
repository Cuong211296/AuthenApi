/**
 * Page buttons to show for a zero-based `current` page out of `total`: first, last, and the pages around
 * the current one, with 'gap' markers where pages are skipped. e.g. (5, 10) -> [0,'gap',4,5,6,'gap',9].
 */
export function pageItems(current, total, siblings = 1) {
  if (total <= 0) return [];
  const pages = new Set([0, total - 1]);
  for (let p = current - siblings; p <= current + siblings; p++) {
    if (p >= 0 && p < total) pages.add(p);
  }
  const sorted = [...pages].sort((a, b) => a - b);
  const out = [];
  sorted.forEach((p, i) => {
    if (i > 0) {
      const gap = p - sorted[i - 1];
      if (gap === 2) out.push(p - 1); // a single hidden page is shown instead of an ellipsis
      else if (gap > 2) out.push('gap');
    }
    out.push(p);
  });
  return out;
}
