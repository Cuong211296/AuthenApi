import { Fragment, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion, useReducedMotion } from 'framer-motion';
import { api } from '../../api/client.js';
import { formatVnd } from '../../utils/money.js';
import Button from '../../components/ui/Button.jsx';
import { ArrowIcon, RefreshIcon, ShieldIcon, TruckIcon } from '../../components/ui/icons.jsx';
import { EASE, fadeUp, stagger } from '../../motion/variants.js';
import { optimizeImageUrl } from '../../utils/image.js';

const HEADLINE = [
  [{ t: 'Phong' }, { t: 'cách' }],
  [{ t: 'của' }, { t: 'bạn,' }],
  [{ t: 'bắt' }, { t: 'đầu' }, { t: 'từ', em: true }, { t: 'đây.', em: true }],
];

const word = {
  hidden: { y: '110%' },
  show: { y: '0%', transition: { duration: 0.8, ease: EASE } },
};

function scrollToId(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
}

/** Two catalog photos for the hero composition (first page, unfiltered). */
function useHeroProducts() {
  const [items, setItems] = useState(null);
  useEffect(() => {
    let ignore = false;
    api('GET', '/products?page=0&size=8', undefined, { auth: false })
      .then((r) => { if (!ignore) setItems(r.items.filter((p) => p.imageUrl).slice(0, 2)); })
      .catch(() => { if (!ignore) setItems([]); });
    return () => { ignore = true; };
  }, []);
  return items;
}

function HeroArt() {
  const items = useHeroProducts();
  const reduce = useReducedMotion();
  const [main, side] = items ?? [];
  const float = (delay) => (reduce ? {} : {
    animate: { y: [0, -10, 0] },
    transition: { duration: 7, ease: 'easeInOut', repeat: Infinity, delay },
  });

  return (
    <motion.div
      className="hero__art"
      initial={reduce ? { opacity: 0 } : { opacity: 0, y: 30, scale: 0.97 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ duration: 0.9, ease: EASE, delay: 0.25 }}
    >
      <span className="hero__ring" aria-hidden="true" />
      <motion.div className="hero__card hero__card--main" {...float(0)}>
        {main ? <img src={optimizeImageUrl(main.imageUrl, 900)} alt={main.name} fetchpriority="high" /> : <span className="hero__card-fill" aria-hidden="true" />}
      </motion.div>
      <motion.div className="hero__card hero__card--side" {...float(1.2)}>
        {side ? <img src={optimizeImageUrl(side.imageUrl, 600)} alt={side.name} /> : <span className="hero__card-fill hero__card-fill--alt" aria-hidden="true" />}
      </motion.div>
      {main && (
        <Link to={`/products/${main.slug}`} className="hero__tag">
          <span className="hero__tag-label">Nổi bật</span>
          <span className="hero__tag-name">{main.name}</span>
          <span className="hero__tag-price tabular">{formatVnd(main.basePrice)}</span>
        </Link>
      )}
    </motion.div>
  );
}

export default function Hero() {
  const reduce = useReducedMotion();
  return (
    <section className="hero" aria-labelledby="hero-title">
      <div className="hero__mesh" aria-hidden="true">
        <span className="hero__blob hero__blob--a" />
        <span className="hero__blob hero__blob--b" />
        <span className="hero__blob hero__blob--c" />
      </div>
      <div className="container hero__inner">
        <motion.div className="hero__copy" initial="hidden" animate="show" variants={stagger(0.05, 0.08)}>
          <motion.p className="eyebrow hero__eyebrow" variants={fadeUp}>Bộ sưu tập Thu – Đông 2026</motion.p>
          <h1 id="hero-title" className="display hero__title">
            {HEADLINE.map((line, li) => (
              <span className="hero__line" key={li}>
                {li > 0 && ' '}
                {line.map((w, wi) => (
                  <Fragment key={wi}>
                    <span className="hero__mask">
                      <motion.span className={`hero__word ${w.em ? 'hero__word--em' : ''}`} variants={reduce ? undefined : word}>
                        {w.t}
                      </motion.span>
                    </span>
                    {wi < line.length - 1 && ' '}
                  </Fragment>
                ))}
              </span>
            ))}
          </h1>
          <motion.p className="hero__lead" variants={fadeUp}>
            Những thiết kế tối giản, chất liệu dễ chịu và phom dáng vừa vặn, để mỗi ngày mặc đẹp trở nên thật nhẹ nhàng.
          </motion.p>
          <motion.div className="hero__ctas" variants={fadeUp}>
            <Button size="lg" iconRight={<ArrowIcon size={18} />} onClick={() => scrollToId('product-results')}>Mua sắm ngay</Button>
            <Button size="lg" variant="ghost" onClick={() => scrollToId('collection-title')}>Xem bộ sưu tập</Button>
          </motion.div>
          <motion.ul className="hero__perks" variants={fadeUp}>
            <li><TruckIcon size={18} /> Giao nhanh 2–5 ngày</li>
            <li><RefreshIcon size={18} /> Đổi trả 30 ngày</li>
            <li><ShieldIcon size={18} /> Thanh toán an toàn</li>
          </motion.ul>
        </motion.div>
        <HeroArt />
      </div>
    </section>
  );
}
