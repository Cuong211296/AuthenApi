import { useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { CloseIcon } from './icons.jsx';
import { useDialog } from './useDialog.js';
import { EASE } from '../../motion/variants.js';
import './Modal.css';
import './Drawer.css';

/**
 * Centered modal dialog. Controlled by `open` / `onClose`. `title` labels it (visually hidden with `hideTitle`).
 * variant "media" gives a dark, borderless frame for an image lightbox.
 */
export default function Modal({ open, onClose, title, hideTitle = false, variant = 'default', initialFocusRef, children, className = '' }) {
  const panelRef = useRef(null);
  const titleId = useId();
  const reduce = useReducedMotion();
  useDialog({ open, containerRef: panelRef, onClose, initialFocusRef });

  return createPortal(
    <AnimatePresence>
      {open && (
        <div className={`ui-modal-root ui-modal-root--${variant}`}>
          <motion.div
            className="ui-overlay"
            onClick={onClose}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
            aria-hidden="true"
          />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            tabIndex={-1}
            className={`ui-modal ui-modal--${variant} ${className}`}
            initial={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.96, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0, transition: { duration: 0.3, ease: EASE } }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.97, transition: { duration: 0.15 } }}
          >
            <div className="ui-modal__head">
              <h2 id={titleId} className={hideTitle ? 'sr-only' : 'ui-modal__title'}>{title}</h2>
              <button type="button" className="ui-icon-btn ui-modal__close" aria-label="Đóng" onClick={onClose}>
                <CloseIcon />
              </button>
            </div>
            <div className="ui-modal__body">{children}</div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
