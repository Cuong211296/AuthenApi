import { motion, useReducedMotion } from 'framer-motion';
import { fade, fadeUp } from './variants.js';

/**
 * Scroll-reveal wrapper: fades (and lifts, unless reduced motion) its children the first time they enter the viewport.
 * `as` picks the rendered element, `delay` offsets the animation, extra props go to the element.
 */
export default function Reveal({ as = 'div', delay = 0, variants, children, ...rest }) {
  const reduce = useReducedMotion();
  const Tag = motion[as] ?? motion.div;
  const base = variants ?? (reduce ? fade : fadeUp);
  const chosen = delay
    ? { ...base, show: { ...base.show, transition: { ...base.show?.transition, delay } } }
    : base;
  return (
    <Tag
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: '-60px' }}
      variants={chosen}
      {...rest}
    >
      {children}
    </Tag>
  );
}
