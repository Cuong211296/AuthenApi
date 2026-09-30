import { useEffect, useRef } from 'react';

const FOCUSABLE = [
  'a[href]', 'area[href]', 'button:not([disabled])', 'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])', 'textarea:not([disabled])', 'iframe', '[tabindex]:not([tabindex="-1"])',
  '[contenteditable="true"]',
].join(',');

let lockCount = 0;
let savedOverflow = '';
let savedPadding = '';

function lockScroll() {
  if (lockCount++ > 0) return;
  const body = document.body;
  const scrollbar = window.innerWidth - document.documentElement.clientWidth;
  savedOverflow = body.style.overflow;
  savedPadding = body.style.paddingRight;
  body.style.overflow = 'hidden';
  if (scrollbar > 0) body.style.paddingRight = `${scrollbar}px`;
}

function unlockScroll() {
  if (--lockCount > 0) return;
  lockCount = 0;
  document.body.style.overflow = savedOverflow;
  document.body.style.paddingRight = savedPadding;
}

export function focusableIn(container) {
  if (!container) return [];
  return [...container.querySelectorAll(FOCUSABLE)].filter(
    (el) => !el.hasAttribute('inert') && el.getClientRects().length > 0,
  );
}

/**
 * Modal dialog behavior while `open`: moves focus inside `containerRef` (to `initialFocusRef` or the first
 * focusable element), traps Tab / Shift+Tab, closes on Escape, locks body scroll, and restores focus to the
 * element that was focused before opening once it closes.
 */
export function useDialog({ open, containerRef, onClose, initialFocusRef }) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return undefined;
    const previouslyFocused = document.activeElement;
    lockScroll();

    // Wait a frame so the portal content is mounted and measurable.
    const raf = requestAnimationFrame(() => {
      const container = containerRef.current;
      const target = initialFocusRef?.current ?? focusableIn(container)[0] ?? container;
      target?.focus({ preventScroll: true });
    });

    function onKeyDown(e) {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onCloseRef.current?.();
        return;
      }
      if (e.key !== 'Tab') return;
      const container = containerRef.current;
      const items = focusableIn(container);
      if (items.length === 0) {
        e.preventDefault();
        container?.focus();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      const active = document.activeElement;
      if (e.shiftKey && (active === first || !container.contains(active))) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && (active === last || !container.contains(active))) {
        e.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', onKeyDown);
    return () => {
      cancelAnimationFrame(raf);
      document.removeEventListener('keydown', onKeyDown);
      unlockScroll();
      if (previouslyFocused && typeof previouslyFocused.focus === 'function' && document.contains(previouslyFocused)) {
        previouslyFocused.focus({ preventScroll: true });
      }
    };
  }, [open, containerRef, initialFocusRef]);
}
