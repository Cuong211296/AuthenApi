#!/usr/bin/env node
/**
 * Screenshot helper for visual checks (Playwright + the installed Microsoft Edge).
 *
 * Library use:
 *   import { launch, openPage, login, shot } from './scripts/shot.mjs';
 *   const browser = await launch();
 *   const page = await openPage(browser, '/', { width: 390, height: 844 });
 *   await shot(page, 'home-mobile');
 *
 * CLI use (from shop-ui/):
 *   node scripts/shot.mjs <path> <name> [--w 1280] [--h 800] [--login] [--viewport-only] [--scroll <selector>]
 *
 * Env: SHOT_DIR (output folder, default: OS temp dir /shop-ui-shots), SHOT_BASE (default http://localhost:5173),
 *      SHOT_USER / SHOT_PASS (default smokeuser1 / password123).
 * Screenshots are for local review only; never commit them.
 */
import { mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright';

export const BASE = process.env.SHOT_BASE || 'http://localhost:5173';
export const SHOT_DIR = resolve(process.env.SHOT_DIR || join(tmpdir(), 'shop-ui-shots'));
export const VIEWPORTS = { desktop: { width: 1280, height: 800 }, mobile: { width: 390, height: 844 } };
const USER = process.env.SHOT_USER || 'smokeuser1';
const PASS = process.env.SHOT_PASS || 'password123';

export function launch(options = {}) {
  return chromium.launch({ channel: 'msedge', ...options });
}

/** Waits for the network to settle, web fonts to load and running animations (page transitions) to finish. */
export async function settle(page, extraMs = 400) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.evaluate(() => document.fonts.ready).catch(() => {});
  await page.waitForTimeout(extraMs);
}

/** New context + page at the viewport (a `{width,height}` or 'desktop' | 'mobile'), opened at `path`. */
export async function openPage(browser, path = '/', viewport = 'desktop', { reducedMotion = 'no-preference' } = {}) {
  const vp = typeof viewport === 'string' ? VIEWPORTS[viewport] : viewport;
  const isMobile = vp.width < 600;
  const context = await browser.newContext({
    viewport: vp,
    deviceScaleFactor: isMobile ? 2 : 1,
    hasTouch: isMobile,
    reducedMotion,
    locale: 'vi-VN',
  });
  const page = await context.newPage();
  page.on('pageerror', (err) => console.error(`[pageerror] ${err.message}`));
  page.on('console', (msg) => { if (msg.type() === 'error') console.error(`[console] ${msg.text()}`); });
  await page.goto(BASE + path, { waitUntil: 'domcontentloaded' });
  await settle(page);
  return page;
}

/** Logs in through the real login form, then returns to `returnTo` (or stays where the app redirects). */
export async function login(page, { username = USER, password = PASS, returnTo } = {}) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel(/Tên đăng nhập/i).fill(username);
  await page.getByLabel(/^Mật khẩu/i).fill(password);
  await page.getByRole('button', { name: /^Đăng nhập$/ }).click();
  await page.waitForURL((url) => !url.pathname.endsWith('/login'), { timeout: 10000 });
  if (returnTo) await page.goto(BASE + returnTo, { waitUntil: 'domcontentloaded' });
  await settle(page);
}

/** Scrolls to the bottom in viewport steps (triggering scroll-reveal animations), then restores the position. */
export async function scrollThrough(page) {
  const start = await page.evaluate(() => window.scrollY);
  const height = await page.evaluate(() => document.documentElement.scrollHeight);
  const step = page.viewportSize().height * 0.8;
  for (let y = 0; y < height; y += step) {
    await page.evaluate((top) => window.scrollTo({ top, behavior: 'instant' }), y);
    await page.waitForTimeout(120);
  }
  await page.evaluate((top) => window.scrollTo({ top, behavior: 'instant' }), start);
  await page.waitForTimeout(700);
}

/**
 * Saves `<SHOT_DIR>/<name>.png` and returns the path. Full page by default (scrolling through first so
 * scroll-reveal content is visible); `fullPage: false` captures only the current viewport.
 */
export async function shot(page, name, { fullPage = true } = {}) {
  mkdirSync(SHOT_DIR, { recursive: true });
  if (fullPage) {
    await scrollThrough(page);
    // Capture from the top so the sticky header sits where a visitor first sees it.
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    await page.waitForTimeout(300);
  }
  const file = join(SHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage, animations: 'disabled' });
  console.log(file);
  return file;
}

async function cli(argv) {
  const [path, name, ...rest] = argv;
  if (!path || !name) {
    console.error('usage: node scripts/shot.mjs <path> <name> [--w 1280] [--h 800] [--login] [--viewport-only] [--scroll <selector>]');
    process.exit(1);
  }
  const flag = (n) => rest.includes(n);
  const value = (n, d) => (rest.includes(n) ? rest[rest.indexOf(n) + 1] : d);
  const browser = await launch();
  try {
    const page = await openPage(browser, flag('--login') ? '/' : path, { width: Number(value('--w', 1280)), height: Number(value('--h', 800)) });
    if (flag('--login')) await login(page, { returnTo: path });
    const selector = value('--scroll');
    if (selector) {
      await page.locator(selector).first().scrollIntoViewIfNeeded();
      await settle(page, 700);
    }
    await shot(page, name, { fullPage: !flag('--viewport-only') });
  } finally {
    await browser.close();
  }
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  cli(process.argv.slice(2)).catch((e) => { console.error(e); process.exit(1); });
}
