import { useState } from 'react';
import { CloseIcon, SearchIcon } from '../../components/ui/icons.jsx';
import { Skeleton } from '../../components/ui/Skeleton.jsx';
import { initialsOf } from '../../utils/initials.js';

/** Square product image with a gradient + initials fallback when there is no image or it fails to load. */
export function Thumb({ src, name, size = 48 }) {
  const [failed, setFailed] = useState(false);
  const showImage = src && !failed;
  return (
    <span className="ad-thumb" style={{ '--thumb': `${size}px` }}>
      {showImage ? <img src={src} alt="" loading="lazy" onError={() => setFailed(true)} /> : <span aria-hidden="true">{initialsOf(name) || '·'}</span>}
    </span>
  );
}

/** Pill search field with an icon and a clear button. Controlled. */
export function SearchBox({ value, onChange, label, placeholder }) {
  return (
    <div className="ad-search" role="search">
      <label className="sr-only" htmlFor="ad-search-input">{label}</label>
      <SearchIcon size={20} className="ad-search__icon" />
      <input
        id="ad-search-input"
        type="search"
        autoComplete="off"
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Escape' && value) { e.preventDefault(); onChange(''); } }}
      />
      {value && (
        <button type="button" className="ad-search__clear" aria-label="Xoá tìm kiếm" onClick={() => onChange('')}>
          <CloseIcon size={18} />
        </button>
      )}
    </div>
  );
}

/** Placeholder rows while a list loads. */
export function SkeletonRows({ count = 6, label = 'Đang tải' }) {
  return (
    <div role="status" aria-label={label}>
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="ad-skelrow" aria-hidden="true">
          <Skeleton width={48} height={48} radius={12} />
          <div className="ad-skelrow__grow">
            <Skeleton variant="line" width="42%" />
            <Skeleton variant="line" width="24%" height={10} />
          </div>
          <Skeleton variant="line" width={90} />
          <Skeleton variant="line" width={72} height={24} />
        </div>
      ))}
    </div>
  );
}
