/** Up to two initials from a product name, used by no-image fallbacks ("Áo thun trơn" -> "ÁT"). */
export function initialsOf(name = '') {
  return name.trim().split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() ?? '').join('');
}
