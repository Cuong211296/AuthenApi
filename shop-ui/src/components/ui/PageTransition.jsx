import { motion, useReducedMotion } from 'framer-motion';
import { pageTransition, pageTransitionReduced } from '../../motion/variants.js';

/** Wraps a route's content: fades and slides it 12px up on enter (fade only with reduced motion). */
export default function PageTransition({ children, className }) {
  const reduce = useReducedMotion();
  const v = reduce ? pageTransitionReduced : pageTransition;
  return (
    <motion.div className={className} initial={v.initial} animate={v.animate} exit={v.exit}>
      {children}
    </motion.div>
  );
}
