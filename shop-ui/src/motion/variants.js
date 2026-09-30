/** Shared motion vocabulary (see the design brief): one easing curve, three durations, one stagger. */
export const EASE = [0.22, 1, 0.36, 1];
export const DURATION = { micro: 0.15, standard: 0.3, page: 0.5 };
export const STAGGER = 0.06;
export const SPRING = { type: 'spring', stiffness: 380, damping: 34 };

export const fade = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { duration: DURATION.standard, ease: EASE } },
};

export const fadeUp = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { duration: DURATION.page, ease: EASE } },
};

/** Parent that staggers children using the `hidden`/`show` labels. */
export function stagger(delayChildren = 0, staggerChildren = STAGGER) {
  return {
    hidden: {},
    show: { transition: { delayChildren, staggerChildren } },
  };
}

export const pageTransition = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0, transition: { duration: DURATION.page, ease: EASE } },
  exit: { opacity: 0, y: -8, transition: { duration: DURATION.micro, ease: EASE } },
};

/** Reduced-motion versions only fade, never translate. */
export const pageTransitionReduced = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: DURATION.micro } },
  exit: { opacity: 0, transition: { duration: DURATION.micro } },
};
