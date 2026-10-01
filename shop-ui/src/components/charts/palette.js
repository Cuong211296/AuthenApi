/**
 * Chart colors for the admin statistics (light surface only: the shop has no dark mode).
 *
 * CATEGORICAL - series identity, assigned in this fixed order and never cycled (past 8 -> OTHER gray, fold the tail).
 * Slot 1 is the shop accent (--accent); slots 2-8 are the dataviz skill's reference hues in its validated order with
 * the accent standing in for its orange slot. Validated with the skill's scripts/validate_palette.js against the
 * card surface (--surface #ffffff), 2026-10-01:
 *
 *   node validate_palette.js "#c8501e,#2a78d6,#1baf7a,#eda100,#e87ba4,#008300,#4a3aa7,#e34948" --mode light --surface "#ffffff"
 *   [PASS] Lightness band         all 8 inside L 0.43–0.77
 *   [PASS] Chroma floor           all 8 >= 0.1
 *   [PASS] CVD separation         worst adjacent #eda100↔#1baf7a ΔE 9.1 (protan) · tritan 5.8
 *   [PASS] Normal-vision floor    worst adjacent #e87ba4↔#eda100 ΔE 19.6 (normal)
 *   [WARN] Contrast vs surface    below 3:1 — relief required (visible labels or table view): #1baf7a 2.82, #eda100 2.17, #e87ba4 2.69
 *   → ALL CHECKS PASS
 *   node validate_palette.js "#c8501e,#2a78d6,#1baf7a" --mode light --surface "#ffffff" --pairs all
 *   [PASS] CVD separation         worst all-pairs #1baf7a↔#c8501e ΔE 10.9 (deutan) · tritan 9.6
 *   [PASS] Normal-vision floor    worst all-pairs #1baf7a↔#2a78d6 ΔE 24.0 (normal)
 *   [WARN] Contrast vs surface    #1baf7a 2.82 (relief: every chart has a table view and labelled legends)
 *
 * The admin charts use slots 1-2 (accent, blue) and both clear 3:1 (4.54 and 4.42). Slots 3-5 sit below 3:1 and are
 * legal only with the relief channel (legend values / table view), which every chart in this kit ships.
 *
 * SEQUENTIAL - one hue (the accent), light -> dark, for magnitude or ordered classes. Validated as an ordinal ramp:
 *   node validate_palette.js "#e9a27c,#dc7a47,#c8501e,#a83f14,#7d2e0e" --ordinal --mode light --surface "#ffffff"
 *   [PASS] Lightness monotone · [PASS] Adjacent ΔL (all gaps >= 0.06) · [PASS] Light-end contrast #e9a27c 2.12:1 · [PASS] Single hue (spread 9°)
 *
 * SEMANTIC - reserved for meaning (delta good/bad, neutral), taken from the shop status tokens (--success, --danger,
 * --muted). They are text colors (WCAG >= 4.5:1 on white) and are never used as a series color; always paired with an
 * arrow icon and a signed value so color is never the only cue.
 */

export const CATEGORICAL = Object.freeze(['#c8501e', '#2a78d6', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948']);

/** De-emphasis / "Other" gray (context series, folded tail). */
export const OTHER = '#b8afa5';

export const SEQUENTIAL = Object.freeze(['#e9a27c', '#dc7a47', '#c8501e', '#a83f14', '#7d2e0e']);

export const SEMANTIC = Object.freeze({ good: '#1f7a4d', bad: '#b3261e', neutral: '#6f675f' });

/** Series color for slot `index` (0-based); past the eight slots returns OTHER instead of cycling. */
export function seriesColor(index) {
  return Number.isInteger(index) && index >= 0 && index < CATEGORICAL.length ? CATEGORICAL[index] : OTHER;
}

/**
 * Tone of a change: 'good' | 'bad' | 'neutral'. `upIsGood` false for metrics where growth is bad (cancel rate).
 * direction is 'up' | 'down' | 'flat' (see delta() in scales.js); anything else is neutral.
 */
export function deltaTone(direction, upIsGood = true) {
  if (direction === 'up') return upIsGood ? 'good' : 'bad';
  if (direction === 'down') return upIsGood ? 'bad' : 'good';
  return 'neutral';
}

function channel(c) {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
}

/** WCAG relative luminance of a #rrggbb color. */
export function luminance(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(String(hex));
  if (!m) return 0;
  const n = parseInt(m[1], 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
}

/** WCAG contrast ratio between two #rrggbb colors (1..21). */
export function contrastRatio(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

/** Ink (#14110f) or white, whichever reads better on a filled mark of color `hex` (for labels set inside fills). */
export const inkOn = (hex) => (contrastRatio(hex, '#ffffff') >= contrastRatio(hex, '#14110f') ? '#ffffff' : '#14110f');
