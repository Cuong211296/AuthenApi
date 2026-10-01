import Badge from '../../../components/ui/Badge.jsx';
import { ChartFrame, ChartTable, formatCount } from '../../../components/charts/index.js';
import { LOW_STOCK_WARN } from '../../../utils/stats.js';

const COLUMNS = [
  { key: 'productName', label: 'Sản phẩm' },
  { key: 'variant', label: 'Size / màu' },
  { key: 'sku', label: 'SKU' },
  {
    key: 'stock',
    label: 'Tồn kho',
    align: 'right',
    format: (stock) => (stock <= LOW_STOCK_WARN
      ? <Badge tone="warn" dot>{stock <= 0 ? 'Hết hàng' : `Còn ${formatCount(stock)}`}</Badge>
      : formatCount(stock)),
  },
];

/** Variants that are running out (<= 5 in stock, lowest first). Not tied to the selected period. */
export default function LowStockCard({ items = [], loading = false }) {
  const rows = items.map((it, i) => ({
    key: `${i}-${it.sku}`,
    productName: it.productName,
    variant: [it.size, it.color].filter(Boolean).join(' / ') || '—',
    sku: it.sku,
    stock: it.stock,
  }));
  return (
    <ChartFrame
      className="ov-chart ov-chart--wide ov-lowstock"
      title="Sắp hết hàng"
      subtitle="Biến thể còn từ 5 sản phẩm trở xuống, hiện tại (không phụ thuộc kỳ đã chọn)"
      label="Biến thể sắp hết hàng"
      loading={loading}
      height={200}
      empty={!loading && rows.length === 0}
      emptyTitle="Chưa có biến thể nào sắp hết hàng"
    >
      <ChartTable caption="Biến thể sắp hết hàng" columns={COLUMNS} rows={rows} />
    </ChartFrame>
  );
}
