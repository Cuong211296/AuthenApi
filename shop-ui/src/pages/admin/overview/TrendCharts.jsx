import { AreaChart, BarChart, ChartFrame } from '../../../components/charts/index.js';
import { bucketFormatters, chartPoints, ordersTable, revenueTable } from '../../../utils/stats.js';

const REVENUE = [{ key: 'revenue', label: 'Doanh thu' }];
const ORDERS = [{ key: 'orders', label: 'Đơn hàng mới' }];

/**
 * Two separate time series over the same buckets (never a dual axis): revenue as an area line, orders as bars.
 * Each chart has a table twin.
 */
export default function TrendCharts({ data, groupBy, loading = false }) {
  const series = data?.series;
  const points = chartPoints(series);
  const fmt = bucketFormatters(data?.groupBy ?? groupBy);
  const unit = { day: 'ngày', month: 'tháng', year: 'năm' }[data?.groupBy ?? groupBy] ?? 'kỳ';
  return (
    <>
      <ChartFrame
        className="ov-chart"
        title="Doanh thu theo thời gian"
        subtitle={`Giá trị hàng của đơn đã thanh toán, theo ${unit} (₫)`}
        label="Doanh thu theo thời gian"
        loading={loading}
        table={series ? revenueTable(series, data?.groupBy ?? groupBy) : undefined}
      >
        <AreaChart data={points} series={REVENUE} label="Doanh thu theo thời gian" {...fmt} />
      </ChartFrame>
      <ChartFrame
        className="ov-chart"
        title="Đơn hàng mới theo thời gian"
        subtitle={`Số đơn được tạo trong kỳ, theo ${unit}`}
        label="Đơn hàng mới theo thời gian"
        loading={loading}
        table={series ? ordersTable(series, data?.groupBy ?? groupBy) : undefined}
      >
        <BarChart data={points} series={ORDERS} label="Đơn hàng mới theo thời gian" {...fmt} />
      </ChartFrame>
    </>
  );
}
