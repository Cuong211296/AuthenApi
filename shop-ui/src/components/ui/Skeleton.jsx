import './Skeleton.css';

/** Shimmering placeholder. variant: block | line | circle. Hidden from assistive tech. */
export function Skeleton({ variant = 'block', width, height, radius, className = '', style }) {
  return (
    <span
      className={`ui-skel ui-skel--${variant} ${className}`}
      style={{ width, height, borderRadius: radius, ...style }}
      aria-hidden="true"
    />
  );
}

/** Skeleton matching the ProductCard layout (3/4 image + text lines). */
export function SkeletonCard() {
  return (
    <div className="ui-skel-card" aria-hidden="true">
      <Skeleton className="ui-skel-card__media" />
      <Skeleton variant="line" width="38%" height={10} />
      <Skeleton variant="line" width="82%" />
      <Skeleton variant="line" width="44%" />
    </div>
  );
}

export default Skeleton;
