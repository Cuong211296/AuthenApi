import { Link } from 'react-router-dom';
import { BRAND } from './brand.js';
import './Footer.css';

const YEAR = new Date().getFullYear();

export default function Footer() {
  return (
    <footer className="ftr">
      <div className="container">
        <div className="ftr__grid">
          <div className="ftr__brand">
            <p className="ftr__wordmark">{BRAND.first}<span aria-hidden="true">·</span><em>{BRAND.second}</em></p>
            <p className="ftr__blurb">
              Trang phục tối giản, chất liệu tuyển chọn và đường may tỉ mỉ, dành cho nhịp sống hằng ngày của bạn.
            </p>
          </div>
          <div>
            <h2 className="ftr__title">Mua sắm</h2>
            <ul>
              <li><Link to="/">Tất cả sản phẩm</Link></li>
              <li><Link to="/cart">Giỏ hàng</Link></li>
              <li><Link to="/orders">Đơn hàng của tôi</Link></li>
            </ul>
          </div>
          <div>
            <h2 className="ftr__title">Hỗ trợ</h2>
            <ul>
              <li>Đổi trả miễn phí trong 30 ngày</li>
              <li>Giao hàng toàn quốc 2–5 ngày</li>
              <li>Hướng dẫn chọn size</li>
            </ul>
          </div>
          <div>
            <h2 className="ftr__title">Liên hệ</h2>
            <ul>
              <li>339 Lê Văn Sỹ, Quận 3, TP Hồ Chí Minh</li>
              <li>Hotline 1900 0000 (8:00 – 21:00)</li>
              <li>hotro@quinibear.vn</li>
            </ul>
          </div>
        </div>
        <div className="ftr__bottom">
          <p>© {YEAR} {BRAND.full}. Mọi quyền được bảo lưu.</p>
          <ul className="ftr__pay" aria-label="Phương thức thanh toán">
            <li className="ftr__pay-momo">MoMo</li>
            <li>COD</li>
          </ul>
        </div>
      </div>
    </footer>
  );
}
