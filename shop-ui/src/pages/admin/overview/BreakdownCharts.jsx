import { ChartFrame, HBarList, StackedBar, formatCount } from '../../../components/charts/index.js';
import {
  paymentParts, paymentTable, statusItems, statusTable, topProductItems, topProductsTable,
} from '../../../utils/stats.js';

/**
 * Breakdowns of the period: top products by revenue (ranked bars, quantity as secondary text), order status counts
 * (workflow order, one neutral color: status colors are reserved for meaning) and the MoMo/COD revenue split.
 * Every chart carries a table twin with the same numbers.
 */
export default function BreakdownCharts({ data, loading = false }) {
  const products = data?.topProducts ?? [];
  const status = data?.statusBreakdown ?? [];
  const payments = data?.paymentBreakdown ?? [];
  const noOrders = status.every((s) => !s.count);
  return (
    <>
      <ChartFrame
        className="ov-chart ov-chart--top"
        title="Sản phẩm bán chạy"
        subtitle="Top 10 theo doanh thu, đơn đã thanh toán"
        label="Sản phẩm bán chạy theo doanh thu"
        loading={loading}
        height={420}
        empty={!loading && products.length === 0}
        emptyTitle="Chưa bán được sản phẩm nào"
        emptyText="Sản phẩm của các đơn đã thanh toán trong kỳ sẽ xuất hiện tại đây."
        table={topProductsTable(products)}
      >
        <HBarList items={topProductItems(products)} label="Sản phẩm bán chạy theo doanh thu" />
      </ChartFrame>
      <div className="ov-stack">
        <ChartFrame
          className="ov-chart"
          title="Trạng thái đơn hàng"
          subtitle="Số đơn tạo trong kỳ theo trạng thái"
          label="Trạng thái đơn hàng"
          loading={loading}
          height={300}
          empty={!loading && noOrders}
          emptyTitle="Chưa có đơn hàng"
          table={statusTable(status)}
        >
          <HBarList items={statusItems(status)} formatValue={formatCount} ranked={false} label="Trạng thái đơn hàng" />
        </ChartFrame>
        <ChartFrame
          className="ov-chart"
          title="Phương thức thanh toán"
          subtitle="Doanh thu của đơn đã thanh toán theo phương thức"
          label="Doanh thu theo phương thức thanh toán"
          loading={loading}
          height={180}
          table={paymentTable(payments)}
        >
          <StackedBar parts={paymentParts(payments)} totalLabel="Tổng doanh thu" label="Doanh thu theo phương thức thanh toán" emptyText="Chưa có doanh thu" />
        </ChartFrame>
      </div>
    </>
  );
}
