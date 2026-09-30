import { Link } from 'react-router-dom';
import { formatVnd } from '../utils/money.js';

export default function ProductCard({ product }) {
  return (
    <Link to={`/products/${product.slug}`} className="product-card">
      {product.imageUrl
        ? <img src={product.imageUrl} alt={product.name} loading="lazy" />
        : <div className="img-placeholder">Chưa có ảnh</div>}
      <div className="body">
        <div>{product.name}</div>
        <div className="price">{formatVnd(product.basePrice)}</div>
        {product.categoryName && <div className="muted">{product.categoryName}</div>}
      </div>
    </Link>
  );
}
