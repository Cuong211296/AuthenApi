import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { BRAND } from '../../components/layout/brand.js';
import { RefreshIcon, ShieldIcon, TruckIcon } from '../../components/ui/icons.jsx';
import { fadeUp, stagger } from '../../motion/variants.js';
import './Auth.css';

const BENEFITS = [
  { icon: <TruckIcon size={20} />, title: 'Giao nhanh toàn quốc', text: 'Theo dõi đơn hàng từng bước, ngay trong tài khoản.' },
  { icon: <RefreshIcon size={20} />, title: 'Đổi trả trong 7 ngày', text: 'Không vừa ý? Chúng tôi đổi size hoặc hoàn tiền.' },
  { icon: <ShieldIcon size={20} />, title: 'Thanh toán an toàn', text: 'MoMo hoặc COD, thông tin của bạn luôn được bảo mật.' },
];

const list = stagger(0.15, 0.08);

/** Split-screen auth page: decorative brand panel (a slim banner on phones) and the form card. */
export default function AuthLayout({ eyebrow, title, lead, footer, children }) {
  return (
    <div className="container au">
      <div className="au__shell">
        <aside className="au__panel" aria-label={`Giới thiệu ${BRAND.full}`}>
          <span className="au__blob au__blob--a" aria-hidden="true" />
          <span className="au__blob au__blob--b" aria-hidden="true" />
          <span className="au__grain" aria-hidden="true" />
          <Link to="/" className="au__brand" aria-label={`${BRAND.full}, về trang chủ`}>
            {BRAND.first}<span>·</span>{BRAND.second}
          </Link>
          <p className="au__tagline display">
            Chất liệu tử tế, <em>phong cách</em> của riêng bạn.
          </p>
          <motion.ul className="au__benefits" variants={list} initial="hidden" animate="show">
            {BENEFITS.map((b) => (
              <motion.li key={b.title} variants={fadeUp}>
                <span className="au__benefit-icon" aria-hidden="true">{b.icon}</span>
                <span>
                  <strong>{b.title}</strong>
                  <span>{b.text}</span>
                </span>
              </motion.li>
            ))}
          </motion.ul>
        </aside>

        <section className="au__card" aria-labelledby="au-title">
          <p className="eyebrow">{eyebrow}</p>
          <h1 id="au-title" className="au__title">{title}</h1>
          {lead && <p className="au__lead">{lead}</p>}
          {children}
          {footer && <p className="au__footer">{footer}</p>}
        </section>
      </div>
    </div>
  );
}
