import './Marquee.css';

/**
 * Infinite horizontal ticker (pure CSS). The track is rendered twice so the loop is seamless;
 * the copy is hidden from assistive tech. Pauses on hover and stops under reduced motion.
 */
export default function Marquee({ items, speed = 40, label, className = '' }) {
  const track = (hidden) => (
    <ul className="ui-marquee__track" aria-hidden={hidden || undefined}>
      {items.map((item, i) => (
        <li key={i} className="ui-marquee__item">
          <span>{item}</span>
          <span className="ui-marquee__sep" aria-hidden="true">✦</span>
        </li>
      ))}
    </ul>
  );
  return (
    <div className={`ui-marquee ${className}`} style={{ '--marquee-duration': `${speed}s` }} aria-label={label} role={label ? 'region' : undefined}>
      <div className="ui-marquee__inner">
        {track(false)}
        {track(true)}
      </div>
    </div>
  );
}
