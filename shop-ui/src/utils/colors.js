/** Display colors for common English / Vietnamese color names used by product variants. */
const COLORS = {
  white: '#f7f5f0', 'trắng': '#f7f5f0', 'trang': '#f7f5f0', ivory: '#f4efe3', cream: '#efe6d2', kem: '#efe6d2',
  black: '#1a1716', 'đen': '#1a1716', 'den': '#1a1716',
  beige: '#d9c7a7', be: '#d9c7a7',
  navy: '#1f2d4f', 'xanh navy': '#1f2d4f', 'xanh đen': '#1f2d4f',
  blue: '#3b6fb6', 'xanh': '#3b6fb6', 'xanh dương': '#3b6fb6', 'xanh da trời': '#6fa3d8',
  gray: '#8d8a86', grey: '#8d8a86', 'xám': '#8d8a86', 'xam': '#8d8a86', 'ghi': '#8d8a86',
  brown: '#7a4e2d', 'nâu': '#7a4e2d', 'nau': '#7a4e2d',
  orange: '#e0712b', cam: '#e0712b',
  yellow: '#e8c547', 'vàng': '#e8c547', 'vang': '#e8c547',
  red: '#c0392b', 'đỏ': '#c0392b', 'do': '#c0392b',
  green: '#3f7d4e', 'xanh lá': '#3f7d4e', 'xanh la': '#3f7d4e',
  olive: '#6b6b3a', 'xanh rêu': '#6b6b3a', 'rêu': '#6b6b3a',
  pink: '#e7a3b5', 'hồng': '#e7a3b5', 'hong': '#e7a3b5',
  purple: '#6d4c8f', 'tím': '#6d4c8f', 'tim': '#6d4c8f',
  khaki: '#b7a57a',
};

// Longest names first so "xanh lá" wins over "xanh" when matching inside a longer name.
const KEYS_BY_LENGTH = Object.keys(COLORS).sort((a, b) => b.length - a.length);

function normalize(name) {
  return String(name ?? '').normalize('NFC').trim().toLowerCase().replace(/\s+/g, ' ');
}

/**
 * Hex color for a variant color name ("white", "Trắng", "Xanh lá nhạt"...), or `null` when unknown
 * so the UI can fall back to a text chip.
 */
export function colorSwatch(name) {
  const key = normalize(name);
  if (!key) return null;
  if (COLORS[key]) return COLORS[key];
  const words = ` ${key} `;
  const match = KEYS_BY_LENGTH.find((k) => words.includes(` ${k} `));
  return match ? COLORS[match] : null;
}
