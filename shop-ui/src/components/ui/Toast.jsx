import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { CheckIcon, CloseIcon } from './icons.jsx';
import { EASE } from '../../motion/variants.js';
import './Toast.css';

const ToastContext = createContext(null);
const DEFAULT_DURATION = 3500;
const MAX_VISIBLE = 3;
let nextId = 1;

/**
 * Provides `useToast()`: `toast(message, { tone, action, duration })` or `toast({ message, tone, action, duration })`.
 * tone: neutral | success | danger | info. action: { label, to } (router link) or { label, onClick }.
 * Returns the toast id; `dismiss(id)` removes it early and `dismissAll()` clears the stack.
 */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef(new Map());

  const dismiss = useCallback((id) => {
    setToasts((list) => list.filter((t) => t.id !== id));
    clearTimeout(timers.current.get(id));
    timers.current.delete(id);
  }, []);

  const dismissAll = useCallback(() => {
    timers.current.forEach(clearTimeout);
    timers.current.clear();
    setToasts([]);
  }, []);

  const toast = useCallback((input, options = {}) => {
    const opts = typeof input === 'string' ? { ...options, message: input } : input;
    const id = nextId++;
    const item = { id, tone: 'neutral', duration: DEFAULT_DURATION, ...opts };
    setToasts((list) => [...list, item].slice(-MAX_VISIBLE));
    if (item.duration > 0) timers.current.set(id, setTimeout(() => dismiss(id), item.duration));
    return id;
  }, [dismiss]);

  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  const value = useMemo(() => ({ toast, dismiss, dismissAll }), [toast, dismiss, dismissAll]);
  return (
    <ToastContext.Provider value={value}>
      {children}
      <ToastViewport toasts={toasts} dismiss={dismiss} />
    </ToastContext.Provider>
  );
}

function ToastViewport({ toasts, dismiss }) {
  const reduce = useReducedMotion();
  return (
    <div className="ui-toasts" role="status" aria-live="polite" aria-relevant="additions">
      <AnimatePresence initial={false}>
        {toasts.map((t) => (
          <motion.div
            key={t.id}
            layout={!reduce}
            className={`ui-toast ui-toast--${t.tone}`}
            initial={reduce ? { opacity: 0 } : { opacity: 0, y: 16, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1, transition: { duration: 0.3, ease: EASE } }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.96, transition: { duration: 0.15 } }}
          >
            {t.tone === 'success' && <span className="ui-toast__icon"><CheckIcon size={16} strokeWidth={2.25} /></span>}
            <p className="ui-toast__msg">{t.message}</p>
            {t.action && (t.action.to
              ? <Link className="ui-toast__action" to={t.action.to} onClick={() => dismiss(t.id)}>{t.action.label}</Link>
              : <button type="button" className="ui-toast__action" onClick={() => { t.action.onClick?.(); dismiss(t.id); }}>{t.action.label}</button>)}
            <button type="button" className="ui-toast__close" aria-label="Đóng thông báo" onClick={() => dismiss(t.id)}>
              <CloseIcon size={16} />
            </button>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
