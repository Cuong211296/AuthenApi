import { useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { CloseIcon } from './icons.jsx';
import { useDialog } from './useDialog.js';
import { SPRING } from '../../motion/variants.js';
import './Drawer.css';

/**
 * Right-side sliding panel (modal dialog). Controlled by `open` / `onClose`.
 * Focus is trapped inside, Escape and overlay click close it, body scroll is locked, focus is restored.
 * `title` labels the dialog; `footer` renders a sticky bottom area.
 */
export default function Drawer({ open, onClose, title, footer, children, initialFocusRef, width = 440, className = '' }) {
  const panelRef = useRef(null);
  const titleId = useId();
  const reduce = useReducedMotion();
  useDialog({ open, containerRef: panelRef, onClose, initialFocusRef });

  return createPortal(
    <AnimatePresence>
      {open && (
        <div className="ui-drawer-root">
          <motion.div
            className="ui-overlay"
            onClick={onClose}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
            aria-hidden="true"
          />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            tabIndex={-1}
            className={`ui-drawer ${className}`}
            style={{ '--drawer-w': `${width}px` }}
            initial={reduce ? { opacity: 0 } : { x: '100%' }}
            animate={reduce ? { opacity: 1 } : { x: 0 }}
            exit={reduce ? { opacity: 0 } : { x: '100%' }}
            transition={reduce ? { duration: 0.15 } : SPRING}
          >
            <div className="ui-drawer__head">
              <h2 id={titleId} className="ui-drawer__title">{title}</h2>
              <button type="button" className="ui-icon-btn" aria-label="Đóng" onClick={onClose}>
                <CloseIcon />
              </button>
            </div>
            <div className="ui-drawer__body">{children}</div>
            {footer && <div className="ui-drawer__foot">{footer}</div>}
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
