import { KPI_CARDS } from '../../../utils/stats.js';
import KpiCard from './KpiCard.jsx';

/** The grid of KPI tiles. `loading` renders skeleton tiles with the real labels so nothing jumps when data arrives. */
export default function KpiGrid({ data, loading = false }) {
  return (
    <section className="ov-kpis" aria-label="Chỉ số chính">
      {KPI_CARDS.map((cfg) => (
        <KpiCard key={cfg.key} cfg={cfg} kpis={data?.kpis} series={data?.series} loading={loading} />
      ))}
    </section>
  );
}
