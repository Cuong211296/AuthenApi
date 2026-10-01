import { useState } from 'react';
import Button from '../../components/ui/Button.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import { AlertIcon, ReceiptIcon } from '../../components/ui/icons.jsx';
import useStatsOverview from '../../hooks/useStatsOverview.js';
import {
  defaultGroupBy, detectPreset, isEmptyPeriod, isGroupByAllowed, presetRange, resolveGroupBy, validateRange,
} from '../../utils/stats.js';
import AdminPageHeader from './AdminPageHeader.jsx';
import BreakdownCharts from './overview/BreakdownCharts.jsx';
import KpiGrid from './overview/KpiGrid.jsx';
import LowStockCard from './overview/LowStockCard.jsx';
import OverviewToolbar from './overview/OverviewToolbar.jsx';
import TrendCharts from './overview/TrendCharts.jsx';
import './overview/overview.css';

const presetRangeEquals = (key, from, to) => {
  const r = presetRange(key);
  return r.from === from && r.to === to;
};

/**
 * "Tổng quan": revenue, orders and customers of a period versus the previous period (GET /admin/stats/overview).
 * Owns the filters (range in the Vietnam time zone, group by) and the loading / error / empty states; the previous
 * data stays on screen (dimmed) while a new range loads, so the layout does not jump.
 */
export default function AdminOverview() {
  const [range, setRange] = useState(() => presetRange('30d'));
  const [groupBy, setGroupBy] = useState(() => defaultGroupBy(range.from, range.to));
  const [groupTouched, setGroupTouched] = useState(false);
  const [presetKey, setPresetKey] = useState('30d'); // remembered so the 1st of a month does not read as "Hôm nay" after "Tháng này"

  const { from, to } = range;
  const rangeError = validateRange(from, to);
  const valid = !rangeError && isGroupByAllowed(groupBy, from, to);
  const { data, loading, stale, error, retry } = useStatsOverview({ from, to, groupBy, enabled: valid });

  function applyRange(next, touched) {
    setRange(next);
    if (!validateRange(next.from, next.to)) setGroupBy((g) => resolveGroupBy(g, next.from, next.to, touched));
  }
  const onPreset = (key) => {
    setGroupTouched(false);
    setPresetKey(key);
    applyRange(presetRange(key), false);
  };
  const onGroupBy = (value) => {
    setGroupBy(value);
    setGroupTouched(true);
  };

  const showSkeleton = loading && !data;
  const showError = Boolean(error);
  const empty = !showSkeleton && !showError && data && isEmptyPeriod(data);

  let body;
  if (showError) {
    body = (
      <EmptyState
        role="alert"
        tone="danger"
        icon={<AlertIcon size={26} />}
        title="Không tải được số liệu"
        action={<Button variant="dark" onClick={retry}>Thử lại</Button>}
      >
        {error}
      </EmptyState>
    );
  } else if (empty) {
    body = (
      <>
        <EmptyState icon={<ReceiptIcon size={26} />} title="Chưa có đơn hàng trong kỳ này">
          Thử chọn một khoảng thời gian khác. Các biến thể sắp hết hàng vẫn hiển thị bên dưới.
        </EmptyState>
        <div className="ov-grid">
          <LowStockCard items={data.lowStock} />
        </div>
      </>
    );
  } else {
    body = (
      <>
        <KpiGrid data={data} loading={showSkeleton} />
        <div className="ov-grid">
          <TrendCharts data={data} groupBy={groupBy} loading={showSkeleton} />
          <BreakdownCharts data={data} loading={showSkeleton} />
          <LowStockCard items={data?.lowStock} loading={showSkeleton} />
        </div>
      </>
    );
  }

  return (
    <div className="ov">
      <AdminPageHeader title="Tổng quan" description="Doanh thu, đơn hàng và khách hàng của cửa hàng." />
      <OverviewToolbar
        from={from}
        to={to}
        preset={presetKey && presetRangeEquals(presetKey, from, to) ? presetKey : detectPreset(from, to)}
        groupBy={groupBy}
        rangeError={rangeError}
        onPreset={onPreset}
        onFrom={(v) => applyRange({ from: v, to }, groupTouched)}
        onTo={(v) => applyRange({ from, to: v }, groupTouched)}
        onGroupBy={onGroupBy}
        onRefresh={retry}
        refreshing={loading}
      />
      <p className="sr-only" role="status">{loading ? 'Đang tải số liệu' : ''}</p>
      <div className={`ov-body ${stale || (rangeError && data) ? 'is-stale' : ''}`} aria-busy={loading || undefined}>
        {body}
      </div>
    </div>
  );
}
