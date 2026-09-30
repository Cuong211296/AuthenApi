import { motion, useReducedMotion } from 'framer-motion';
import { EASE } from '../../motion/variants.js';
import './StatusMark.css';

/**
 * Large round status glyph. kind: 'success' (circle + check drawn as an SVG path), 'danger' (circle + cross),
 * 'warn' (circle + exclamation) or 'pending' (spinner). Decorative: pair it with text.
 * Under reduced motion the paths are simply drawn.
 */
export default function StatusMark({ kind = 'success', size = 96 }) {
  const reduce = useReducedMotion();

  if (kind === 'pending') {
    return (
      <span className="ui-mark ui-mark--pending" style={{ width: size, height: size }} aria-hidden="true">
        <span className="spinner ui-mark__spinner" />
      </span>
    );
  }

  const draw = (delay, duration = 0.55) => ({
    initial: reduce ? false : { pathLength: 0, opacity: 0 },
    animate: { pathLength: 1, opacity: 1 },
    transition: { duration, delay, ease: EASE },
  });

  return (
    <span className={`ui-mark ui-mark--${kind}`} style={{ width: size, height: size }} aria-hidden="true">
      <svg viewBox="0 0 96 96" width={size} height={size} fill="none" strokeLinecap="round" strokeLinejoin="round">
        <motion.circle cx="48" cy="48" r="42" strokeWidth="4" {...draw(0, 0.7)} />
        {kind === 'success' && <motion.path d="M29 49.5 42.5 63 68 35" strokeWidth="6" {...draw(0.45)} />}
        {kind === 'danger' && (
          <>
            <motion.path d="M34 34 62 62" strokeWidth="6" {...draw(0.45, 0.35)} />
            <motion.path d="M62 34 34 62" strokeWidth="6" {...draw(0.65, 0.35)} />
          </>
        )}
        {kind === 'warn' && (
          <>
            <motion.path d="M48 28v24" strokeWidth="6" {...draw(0.45, 0.35)} />
            <motion.path d="M48 65v.5" strokeWidth="7" {...draw(0.7, 0.2)} />
          </>
        )}
      </svg>
    </span>
  );
}
