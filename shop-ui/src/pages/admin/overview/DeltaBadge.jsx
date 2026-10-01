import { ArrowIcon } from '../../../components/ui/icons.jsx';
import { deltaView } from '../../../utils/stats.js';

/**
 * Change versus the previous period: direction arrow + signed value, tone from the KPI (good / bad / neutral), so color is
 * never the only cue. Renders nothing when there is nothing to compare (kind "na"). "so với kỳ trước" is stated in the
 * toolbar; the accessible name repeats it.
 */
export default function DeltaBadge({ cfg, current, previous }) {
  const view = deltaView(cfg, current, previous);
  if (view.kind === 'na') return null;
  const arrow = view.direction === 'up' || view.direction === 'down' ? view.direction : null;
  return (
    <span className={`ov-delta ov-delta--${view.tone}`} role="img" aria-label={view.label} title={view.label}>
      {arrow && <ArrowIcon size={13} direction={arrow} strokeWidth={2.25} />}
      {view.text}
    </span>
  );
}
