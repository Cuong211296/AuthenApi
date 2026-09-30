import { motion } from 'framer-motion';
import { EASE } from '../../motion/variants.js';
import { CheckIcon } from './icons.jsx';
import './ProgressSteps.css';

const STATE_HINT = { done: ' (đã xong)', current: ' (hiện tại)', todo: '' };

/**
 * Horizontal progress indicator. steps: [{ key, label, state: 'done' | 'current' | 'todo' }].
 * The connector before a step fills once that step is reached. The current step has aria-current="step".
 */
export default function ProgressSteps({ steps, label, className = '' }) {
  return (
    <ol className={`ui-steps ${className}`} aria-label={label} style={{ '--steps': steps.length }}>
      {steps.map((step, i) => (
        <li
          key={step.key}
          className={`ui-steps__item is-${step.state}`}
          aria-current={step.state === 'current' ? 'step' : undefined}
        >
          {i > 0 && (
            <span className="ui-steps__bar" aria-hidden="true">
              <motion.span
                className="ui-steps__fill"
                initial={{ scaleX: 0 }}
                animate={{ scaleX: step.state === 'todo' ? 0 : 1 }}
                transition={{ duration: 0.6, ease: EASE, delay: 0.05 }}
              />
            </span>
          )}
          <span className="ui-steps__dot" aria-hidden="true">
            {step.state === 'done' ? <CheckIcon size={16} strokeWidth={2.4} /> : i + 1}
          </span>
          <span className="ui-steps__label">
            {step.label}
            <span className="sr-only">{STATE_HINT[step.state]}</span>
          </span>
        </li>
      ))}
    </ol>
  );
}
