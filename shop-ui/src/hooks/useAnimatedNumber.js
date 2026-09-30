import { useEffect, useRef, useState } from 'react';
import { useReducedMotion } from 'framer-motion';
import { tweenValue } from '../utils/tween.js';

/** Returns `value`, counting smoothly from the previously shown number when it changes (instant under reduced motion). */
export function useAnimatedNumber(value, duration = 450) {
  const reduce = useReducedMotion();
  const [shown, setShown] = useState(value);
  const shownRef = useRef(value);

  useEffect(() => {
    if (reduce || shownRef.current === value) {
      shownRef.current = value;
      setShown(value);
      return undefined;
    }
    const from = shownRef.current;
    const start = performance.now();
    let raf;
    const step = (now) => {
      const t = (now - start) / duration;
      const next = tweenValue(from, value, t);
      shownRef.current = next;
      setShown(next);
      if (t < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [value, duration, reduce]);

  return shown;
}
