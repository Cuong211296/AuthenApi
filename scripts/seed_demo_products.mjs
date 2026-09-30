// Seeds demo clothing products (with photos and variants) through the admin API.
// Usage (backend running):  node scripts/seed_demo_products.mjs
// Reads ADMIN_PASSWORD from .env. Safe to re-run: existing slugs are skipped.
import { readFileSync } from 'node:fs';

const BASE = process.env.API_BASE || 'http://localhost:8081/identity';
const env = Object.fromEntries(
  readFileSync(new URL('../.env', import.meta.url), 'utf8')
    .split(/\r?\n/)
    .filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
);

const img = (id) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=900&q=80`;

const CATEGORIES = [
  { name: 'Áo thun', slug: 'ao-thun' },
  { name: 'Áo khoác', slug: 'ao-khoac' },
  { name: 'Quần', slug: 'quan' },
];

const PRODUCTS = [
  { cat: 'ao-thun', name: 'Áo thun cotton trơn', slug: 'ao-thun-cotton-tron', price: 199000, photo: '1521572163474-6864f9cf17ab', colors: ['white', 'black'], description: 'Áo thun cotton 100% mềm mại, form regular dễ phối đồ, thấm hút mồ hôi tốt.' },
  { cat: 'ao-thun', name: 'Áo thun graphic Peace', slug: 'ao-thun-graphic-peace', price: 249000, photo: '1503342217505-b0a15ec3261c', colors: ['black'], description: 'Áo thun in hình bàn tay Peace phong cách đường phố, chất liệu cotton dày dặn.' },
  { cat: 'ao-thun', name: 'Áo thun Original Neko', slug: 'ao-thun-original-neko', price: 279000, photo: '1576566588028-4147f3842f27', colors: ['beige', 'white'], description: 'Áo thun in mèo thần tài nổi bật, màu be nhẹ nhàng, form rộng thoải mái.' },
  { cat: 'ao-thun', name: 'Áo thun đen tối giản', slug: 'ao-thun-den-toi-gian', price: 229000, photo: '1583743814966-8936f5b7be1a', colors: ['black', 'white'], description: 'Thiết kế tối giản với dòng chữ nhỏ trước ngực, chất vải dày đứng form.' },
  { cat: 'ao-khoac', name: 'Áo khoác da Biker', slug: 'ao-khoac-da-biker', price: 1290000, photo: '1551028719-00167b16eac5', colors: ['black'], description: 'Áo khoác da biker cổ điển, khóa kéo chéo, lớp lót mềm, phong cách cá tính.' },
  { cat: 'ao-khoac', name: 'Áo khoác Bomber đất nung', slug: 'ao-khoac-bomber-dat-nung', price: 890000, photo: '1591047139829-d91aecb6caea', colors: ['orange', 'black'], description: 'Bomber màu đất nung ấm áp, chất liệu chống gió nhẹ, bo tay và gấu áo co giãn.' },
  { cat: 'ao-khoac', name: 'Bộ vest kẻ caro xanh', slug: 'bo-vest-ke-caro-xanh', price: 2490000, photo: '1594938298603-c8148c4dae35', colors: ['navy'], description: 'Bộ vest ba mảnh họa tiết caro tinh tế, phù hợp công sở và sự kiện.' },
  { cat: 'ao-khoac', name: 'Áo sweater cổ tròn', slug: 'ao-sweater-co-tron', price: 459000, photo: '1620799140408-edc6dcb6d633', colors: ['white', 'gray'], description: 'Sweater nỉ bông mềm, cổ tròn, ấm áp mà vẫn nhẹ nhàng.' },
  { cat: 'quan', name: 'Quần jeans slim đen', slug: 'quan-jeans-slim-den', price: 590000, photo: '1542272604-787c3835535d', colors: ['black', 'blue'], sizes: ['28', '30', '32', '34'], description: 'Quần jeans dáng slim, vải denim co giãn nhẹ, bền màu sau nhiều lần giặt.' },
  { cat: 'quan', name: 'Quần jeans rách gối', slug: 'quan-jeans-rach-goi', price: 650000, photo: '1541099649105-f69ad21f3246', colors: ['blue'], sizes: ['28', '30', '32', '34'], description: 'Jeans xanh đậm rách gối phong cách streetwear, đắp miếng vá thêu nhỏ.' },
  { cat: 'quan', name: 'Quần jogger vàng', slug: 'quan-jogger-vang', price: 420000, photo: '1515886657613-9f3515b0c78f', colors: ['yellow'], description: 'Jogger nỉ mỏng màu vàng nổi bật, lưng thun bo gấu, vận động thoải mái.' },
];

async function call(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res.json();
}

const login = await call('POST', '/auth/token', { username: 'admin', password: env.ADMIN_PASSWORD });
if (!login.result) throw new Error('Admin login failed (code ' + login.code + '). Check ADMIN_PASSWORD in .env');
const token = login.result.token;

const existingCats = (await call('GET', '/categories')).result;
const catId = {};
for (const c of CATEGORIES) {
  const found = existingCats.find((x) => x.slug === c.slug);
  catId[c.slug] = found ? found.id : (await call('POST', '/admin/categories', c, token)).result.id;
}

let created = 0;
for (const [index, p] of PRODUCTS.entries()) {
  const res = await call('POST', '/admin/products', {
    name: p.name, slug: p.slug, description: p.description, categoryId: catId[p.cat],
    basePrice: p.price, imageUrl: img(p.photo), active: true,
  }, token);
  if (!res.result) { console.log(`skip ${p.slug} (code ${res.code})`); continue; }
  const sizes = p.sizes || ['S', 'M', 'L', 'XL'];
  for (const color of p.colors) {
    for (const size of sizes) {
      await call('POST', `/admin/products/${res.result.id}/variants`, {
        size, color, sku: `${p.slug}-${size}-${color}`.toUpperCase(), stock: 6 + ((index * 3 + size.length * 2) % 14),
      }, token);
    }
  }
  created++;
  console.log(`created ${p.name}`);
}
console.log(`Done: ${created} new products.`);
