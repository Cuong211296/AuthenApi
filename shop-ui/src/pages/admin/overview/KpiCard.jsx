import { Skeleton } from '../../../components/ui/Skeleton.jsx';
import Sparkline from '../../../components/charts/Sparkline.jsx';
import { formatVndFull } from '../../../components/charts/scales.js';
import { formatKpiValue, profitNote, sparkValues } from '../../../utils/stats.js';
import DeltaBadge from './DeltaBadge.jsx';

/** One KPI tile: label, value, delta badge vs the previous period, optional sparkline and a secondary line. */
export default function KpiCard({ cfg, kpis, series, loading = false }) {
  const kpi = kpis?.[cfg.key];
  const isProfit = cfg.key === 'profit';
  const unknownProfit = isProfit && !loading && (kpi?.value === null || kpi?.value === undefined);
  const values = !loading && cfg.spark ? sparkValues(series, cfg.spark) : [];
  const showSpark = values.some((v) => Number.isFinite(v));

  let secondary = null;
  if (isProfit && !loading) secondary = profitNote(kpi);
  if (cfg.key === 'revenue' && !loading && kpis?.shippingCollected) {
    secondary = `Phí vận chuyển thu thêm: ${formatVndFull(kpis.shippingCollected.value)}`;
  }

  return (
    <div className={`ov-kpi ${cfg.wide ? 'ov-kpi--wide' : ''}`} data-kpi={cfg.key}>
      <h3 className="ov-kpi__label">{cfg.label}</h3>
      {loading ? (
        <>
          <Skeleton variant="line" width="62%" height={28} />
          <Skeleton variant="line" width="38%" height={20} />
        </>
      ) : (
        <>
          <p className={`ov-kpi__value ${unknownProfit ? 'is-unknown' : ''}`}>
            {unknownProfit ? 'Chưa có giá vốn' : formatKpiValue(cfg, kpi?.value)}
          </p>
          <div className="ov-kpi__foot">
            {!unknownProfit && <DeltaBadge cfg={cfg} current={kpi?.value} previous={kpi?.previous} />}
            {showSpark && <Sparkline className="ov-kpi__spark" values={values} width={96} height={30} />}
          </div>
          {secondary && <p className="ov-kpi__note">{secondary}</p>}
        </>
      )}
    </div>
  );
}
