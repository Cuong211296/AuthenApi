import { forwardRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import Badge from '../ui/Badge.jsx';
import Stepper from '../ui/Stepper.jsx';
import { CloseIcon } from '../ui/icons.jsx';
import { formatVnd } from '../../utils/money.js';
import { maxQuantityFor } from '../../utils/quantity.js';
import { EASE } from '../../motion/variants.js';
import './CartLines.css';

function Thumb({ item }) {
  const [failed, setFailed] = useState(false);
  return (
    <Link to={`/products/${item.productSlug}`} className="cl__thumb" tabIndex={-1} aria-hidden="true">
      {item.imageUrl && !failed
        ? <img src={item.imageUrl} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />
        : <span className="cl__thumb-fallback">{item.productName.slice(0, 1)}</span>}
    </Link>
  );
}

const Line = forwardRef(function Line({ item, compact, busy, onChange, onRemove }, ref) {
  const reduce = useReducedMotion();
  // A rejected change leaves the cart untouched; bumping the key remounts the stepper so it shows the real quantity again.
  const [nonce, setNonce] = useState(0);
  // An unavailable line may hold more than the stock: never let max drop below the quantity ("+" stays disabled).
  const max = Math.max(1, maxQuantityFor(item.stock), item.quantity);

  async function change(q) {
    if (!(await onChange(item.variantId, q))) setNonce((n) => n + 1);
  }

  return (
    <motion.li
      ref={ref}
      layout={reduce ? false : 'position'}
      className={`cl__line ${compact ? 'cl__line--compact' : ''} ${item.available ? '' : 'is-unavailable'}`}
      initial={reduce ? { opacity: 0 } : { opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0, transition: { duration: 0.3, ease: EASE } }}
      exit={reduce ? { opacity: 0 } : { opacity: 0, x: 40, transition: { duration: 0.2 } }}
    >
      <Thumb item={item} />
      <div className="cl__info">
        <Link to={`/products/${item.productSlug}`} className="cl__name">{item.productName}</Link>
        <p className="cl__variant">{item.size} · {item.color}</p>
        {!compact && <p className="cl__unit tabular">{formatVnd(item.unitPrice)}</p>}
        {!item.available && (
          <Badge tone="danger" dot className="cl__badge">
            {item.stock > 0 ? `Chỉ còn ${item.stock} sản phẩm` : 'Hết hàng'}
          </Badge>
        )}
      </div>
      <div className="cl__qty">
        <Stepper
          key={nonce}
          size="sm"
          value={item.quantity}
          max={max}
          disabled={busy}
          label={`Số lượng ${item.productName}, ${item.size} ${item.color}`}
          onChange={change}
        />
      </div>
      <p className="cl__total tabular">{formatVnd(item.lineTotal)}</p>
      <button
        type="button"
        className="cl__remove"
        disabled={busy}
        aria-label={`Xoá ${item.productName}, ${item.size} ${item.color} khỏi giỏ hàng`}
        onClick={() => onRemove(item.variantId)}
      >
        <CloseIcon size={16} />
      </button>
    </motion.li>
  );
});

/** Animated list of cart lines, used by the cart page (roomy rows) and the cart drawer (`compact`). */
export default function CartLines({ items, compact = false, pendingId, busy = pendingId != null, onChange, onRemove }) {
  return (
    <ul className={`cl ${compact ? 'cl--compact' : ''}`}>
      <AnimatePresence initial={false} mode="popLayout">
        {items.map((item) => (
          <Line
            key={item.variantId}
            item={item}
            compact={compact}
            busy={busy}
            onChange={onChange}
            onRemove={onRemove}
          />
        ))}
      </AnimatePresence>
    </ul>
  );
}
