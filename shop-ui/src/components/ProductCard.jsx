import { memo, useState } from 'react';
import { Link } from 'react-router-dom';
import { formatVnd } from '../utils/money.js';
import { initialsOf } from '../utils/initials.js';
import { ArrowIcon } from './ui/icons.jsx';
import './ProductCard.css';

function ProductCard({ product, eager = false }) {
  const [failed, setFailed] = useState(false);
  const showImage = product.imageUrl && !failed;
  return (
    <article className="pc">
      <Link to={`/products/${product.slug}`} className="pc__link">
        <div className="pc__media">
          {showImage ? (
            <img
              src={product.imageUrl}
              alt=""
              loading={eager ? 'eager' : 'lazy'}
              decoding="async"
              onError={() => setFailed(true)}
            />
          ) : (
            <div className="pc__fallback" aria-hidden="true">{initialsOf(product.name)}</div>
          )}
          <span className="pc__quick" aria-hidden="true">
            Xem chi tiết <ArrowIcon size={15} />
          </span>
        </div>
        <div className="pc__body">
          {product.categoryName && <p className="pc__cat">{product.categoryName}</p>}
          <h3 className="pc__name">{product.name}</h3>
          <p className="pc__price tabular">{formatVnd(product.basePrice)}</p>
        </div>
      </Link>
    </article>
  );
}

export default memo(ProductCard);
