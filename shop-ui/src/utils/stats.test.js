import { describe, expect, it } from 'vitest';
import {
  KPI_CARDS, addDays, bucketFormatters, chartPoints, comparisonNote, defaultGroupBy, deltaTone, deltaView, detectPreset,
  formatDateVi, formatKpiValue, formatShortDate, isEmptyPeriod, isGroupByAllowed, isValidIsoDate, paymentParts, paymentTable,
  presetRange, previousRange, profitNote, rangeDays, resolveGroupBy, sparkValues, statusItems, topProductItems, validateRange, vnToday,
} from './stats.js';

const kpi = (key) => KPI_CARDS.find((k) => k.key === key);

describe('vnToday (Asia/Ho_Chi_Minh, not the browser zone)', () => {
  it('rolls over at Vietnam midnight', () => {
    expect(vnToday(new Date('2026-09-30T16:59:59Z'))).toBe('2026-09-30');
    expect(vnToday(new Date('2026-09-30T17:00:00Z'))).toBe('2026-10-01');
  });
  it('handles the year boundary', () => expect(vnToday(new Date('2026-12-31T20:00:00Z'))).toBe('2027-01-01'));
});

describe('date arithmetic', () => {
  it('adds days across months, years and leap days', () => {
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(addDays('2028-03-01', -1)).toBe('2028-02-29');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });
  it('counts inclusive days', () => {
    expect(rangeDays('2026-09-01', '2026-09-01')).toBe(1);
    expect(rangeDays('2026-09-01', '2026-09-30')).toBe(30);
    expect(rangeDays('2026-01-01', '2026-12-31')).toBe(365);
  });
  it('validates ISO dates strictly', () => {
    expect(isValidIsoDate('2026-02-28')).toBe(true);
    for (const bad of ['2026-02-30', '2026-13-01', '26-01-01', '', null, undefined, '2026-1-1']) expect(isValidIsoDate(bad)).toBe(false);
  });
});

describe('presetRange (now = 2026-10-01 00:30 in Vietnam)', () => {
  const now = new Date('2026-09-30T17:30:00Z');
  it('Hôm nay', () => expect(presetRange('today', now)).toEqual({ from: '2026-10-01', to: '2026-10-01' }));
  it('7 ngày is today-6..today', () => expect(presetRange('7d', now)).toEqual({ from: '2026-09-25', to: '2026-10-01' }));
  it('30 ngày is today-29..today', () => {
    const r = presetRange('30d', now);
    expect(r).toEqual({ from: '2026-09-02', to: '2026-10-01' });
    expect(rangeDays(r.from, r.to)).toBe(30);
  });
  it('Tháng này starts on the 1st', () => expect(presetRange('month', now)).toEqual({ from: '2026-10-01', to: '2026-10-01' }));
  it('Tháng này mid-month', () => expect(presetRange('month', new Date('2026-09-14T05:00:00Z'))).toEqual({ from: '2026-09-01', to: '2026-09-14' }));
  it('Năm nay starts on Jan 1', () => expect(presetRange('year', now)).toEqual({ from: '2026-01-01', to: '2026-10-01' }));
  it('detects which preset a range equals', () => {
    expect(detectPreset('2026-09-25', '2026-10-01', now)).toBe('7d');
    expect(detectPreset('2026-01-01', '2026-10-01', now)).toBe('year');
    expect(detectPreset('2026-09-10', '2026-10-01', now)).toBe('custom');
  });
});

describe('previousRange / comparisonNote', () => {
  it('is the same number of days right before from', () => {
    expect(previousRange('2026-09-01', '2026-09-30')).toEqual({ from: '2026-08-02', to: '2026-08-31' });
    expect(previousRange('2026-10-01', '2026-10-01')).toEqual({ from: '2026-09-30', to: '2026-09-30' });
  });
  it('writes the visible note', () => expect(comparisonNote('2026-09-01', '2026-09-30')).toBe('so với kỳ trước (02/08–31/08)'));
  it('falls back to plain text for an invalid range', () => expect(comparisonNote('2026-09-30', '2026-09-01')).toBe('so với kỳ trước'));
  it('formats short and full dates', () => {
    expect(formatShortDate('2026-09-05')).toBe('05/09');
    expect(formatDateVi('2026-09-05')).toBe('05/09/2026');
    expect(formatShortDate('')).toBe('');
  });
});

describe('group by rules', () => {
  it('defaults by range length (day <= 62, month <= 800, else year)', () => {
    expect(defaultGroupBy('2026-09-01', '2026-09-01')).toBe('day');
    expect(defaultGroupBy('2026-08-01', '2026-09-30')).toBe('day'); // 61 days
    expect(defaultGroupBy('2026-08-01', '2026-10-01')).toBe('day'); // 62 days
    expect(defaultGroupBy('2026-07-31', '2026-10-01')).toBe('month'); // 63 days
    expect(defaultGroupBy('2024-08-12', '2026-10-01')).toBe('month'); // 781 days
    expect(defaultGroupBy('2023-01-01', '2026-10-01')).toBe('year');
  });
  it('allows day only up to 366 days', () => {
    expect(isGroupByAllowed('day', '2026-01-01', '2026-12-31')).toBe(true);
    expect(isGroupByAllowed('day', '2025-12-31', '2026-12-31')).toBe(true); // 366 days
    expect(isGroupByAllowed('day', '2025-12-30', '2026-12-31')).toBe(false); // 367 days
    expect(isGroupByAllowed('month', '2020-01-01', '2026-12-31')).toBe(true);
    expect(isGroupByAllowed('year', '2026-09-01', '2026-09-02')).toBe(true);
    expect(isGroupByAllowed('week', '2026-09-01', '2026-09-02')).toBe(false);
  });
  it('resolves: untouched -> default, touched and allowed -> kept, touched but now disallowed -> default', () => {
    expect(resolveGroupBy('month', '2026-09-01', '2026-09-30', false)).toBe('day');
    expect(resolveGroupBy('month', '2026-09-01', '2026-09-30', true)).toBe('month');
    expect(resolveGroupBy('day', '2025-06-01', '2026-09-30', true)).toBe('month');
  });
});

describe('validateRange', () => {
  it('accepts a normal range and the maximum', () => {
    expect(validateRange('2026-09-01', '2026-09-30')).toBe('');
    expect(validateRange('2026-09-01', '2026-09-01')).toBe('');
    expect(validateRange('2023-09-28', '2026-10-01')).toBe(''); // exactly 1100 days
  });
  it('rejects empty and impossible dates', () => {
    expect(validateRange('', '2026-09-30')).toMatch(/hợp lệ/);
    expect(validateRange('2026-02-30', '2026-09-30')).toMatch(/hợp lệ/);
  });
  it('rejects from after to', () => expect(validateRange('2026-09-30', '2026-09-01')).toMatch(/trước hoặc bằng/));
  it('rejects more than 1100 days', () => expect(validateRange('2023-09-27', '2026-10-01')).toMatch(/1100/));
});

describe('KPI config', () => {
  it('formats values per kind', () => {
    expect(formatKpiValue(kpi('revenue'), 1250000)).toBe('1.250.000 ₫');
    expect(formatKpiValue(kpi('orders'), 1234)).toBe('1.234');
    expect(formatKpiValue(kpi('cancelRate'), 0.125)).toBe('12,5%');
    expect(formatKpiValue(kpi('profit'), null)).toBe('—');
  });
  it('lists the sparkline source per KPI', () => {
    expect(kpi('revenue').spark).toBe('revenue');
    expect(kpi('cancelRate').spark).toBeNull();
    expect(KPI_CARDS.map((k) => k.key)).toContain('profit');
  });
  it('derives a good/bad tone, the cancel rate going up is bad', () => {
    expect(deltaTone(kpi('revenue'), { direction: 'up' })).toBe('good');
    expect(deltaTone(kpi('revenue'), { direction: 'down' })).toBe('bad');
    expect(deltaTone(kpi('cancelRate'), { direction: 'up' })).toBe('bad');
    expect(deltaTone(kpi('cancelRate'), 'down')).toBe('good');
    expect(deltaTone(kpi('revenue'), { direction: 'flat' })).toBe('neutral');
    expect(deltaTone(kpi('revenue'), null)).toBe('neutral');
  });
});

describe('deltaView', () => {
  it('shows a signed percent with direction and tone', () => {
    const up = deltaView(kpi('revenue'), 1125, 1000);
    expect(up).toMatchObject({ kind: 'change', direction: 'up', tone: 'good', text: '+12,5%' });
    const down = deltaView(kpi('orders'), 80, 100);
    expect(down).toMatchObject({ direction: 'down', tone: 'bad', text: '−20%' });
  });
  it('is "Mới" when the previous period had none', () => {
    expect(deltaView(kpi('revenue'), 500, 0)).toMatchObject({ kind: 'new', text: 'Mới', tone: 'good' });
    expect(deltaView(kpi('cancelRate'), 0.1, 0)).toMatchObject({ kind: 'new', tone: 'bad' });
  });
  it('is a dash when both are 0 and nothing when unknown', () => {
    expect(deltaView(kpi('revenue'), 0, 0)).toMatchObject({ kind: 'none', text: '—', tone: 'neutral' });
    expect(deltaView(kpi('profit'), null, 1000).kind).toBe('na');
    expect(deltaView(kpi('profit'), 1000, null).kind).toBe('na');
  });
  it('treats an equal value as neutral flat', () => expect(deltaView(kpi('orders'), 10, 10)).toMatchObject({ direction: 'flat', tone: 'neutral', text: '0%' }));
  it('shows percentage points for the cancel rate', () => {
    expect(deltaView(kpi('cancelRate'), 0.12, 0.1)).toMatchObject({ direction: 'up', tone: 'bad', text: '+2 điểm' });
    expect(deltaView(kpi('cancelRate'), 0.05, 0.1)).toMatchObject({ direction: 'down', tone: 'good', text: '−5 điểm' });
  });
  it('has a sentence for screen readers', () => expect(deltaView(kpi('revenue'), 1125, 1000).label).toBe('Tăng 12,5% so với kỳ trước'));
});

describe('profitNote', () => {
  it('states the coverage', () => expect(profitNote({ value: 100, previous: 50, coverage: 0.625 })).toBe('Dựa trên 62% giá trị hàng đã bán có giá vốn'));
  it('never rounds partial coverage up to 100%', () => expect(profitNote({ value: 1, coverage: 0.999 })).toBe('Dựa trên 99% giá trị hàng đã bán có giá vốn'));
  it('shows 100% only for full coverage', () => expect(profitNote({ value: 1, coverage: 1 })).toBe('Dựa trên 100% giá trị hàng đã bán có giá vốn'));
  it('asks for cost prices when profit is unknown', () => {
    expect(profitNote({ value: null, previous: null, coverage: 0 })).toMatch(/giá vốn/);
    expect(profitNote(undefined)).toMatch(/giá vốn/);
  });
});

describe('series adapters', () => {
  const series = [
    { bucket: '2026-09-01', revenue: 100000, orders: 3, paidOrders: 2, profit: 20000 },
    { bucket: '2026-09-02', revenue: 0, orders: 0, paidOrders: 0, profit: null },
  ];
  it('feeds sparklines per key and derives the average order value', () => {
    expect(sparkValues(series, 'revenue')).toEqual([100000, 0]);
    expect(sparkValues(series, 'profit')).toEqual([20000, null]);
    expect(sparkValues(series, 'averageOrderValue')).toEqual([50000, null]);
    expect(sparkValues(series, null)).toEqual([]);
    expect(sparkValues(undefined, 'revenue')).toEqual([]);
  });
  it('normalizes chart points', () => expect(chartPoints([{ bucket: 'x', revenue: null, orders: 2 }])).toEqual([{ bucket: 'x', revenue: 0, orders: 2, paidOrders: 0, profit: null }]));
  it('formats buckets per grouping', () => {
    expect(bucketFormatters('day').formatX('2026-09-05')).toBe('05/09');
    expect(bucketFormatters('month').formatXLong('2026-09')).toBe('Tháng 09/2026');
    expect(bucketFormatters('year').formatX('2026')).toBe('2026');
  });
});

describe('status, payment and product adapters', () => {
  it('lists statuses in workflow order with Vietnamese labels and shares', () => {
    const items = statusItems([
      { status: 'CANCELLED', count: 1 }, { status: 'COMPLETED', count: 3 }, { status: 'PENDING_PAYMENT', count: 0 }, { status: 'SHIPPING', count: 0 },
    ]);
    expect(items.map((i) => i.status)).toEqual(['PENDING_PAYMENT', 'SHIPPING', 'COMPLETED', 'CANCELLED']);
    expect(items[2]).toMatchObject({ label: 'Hoàn thành', value: 3, secondary: '75%' });
    expect(items[3].label).toBe('Đã huỷ');
  });
  it('has no shares when there are no orders', () => expect(statusItems([{ status: 'COMPLETED', count: 0 }])[0].secondary).toBeUndefined());
  it('names payment methods and splits revenue', () => {
    const b = [{ method: 'MOMO', orders: 5, revenue: 900000 }, { method: 'COD', orders: 2, revenue: 100000 }];
    expect(paymentParts(b)).toEqual([{ key: 'MOMO', label: 'MoMo', value: 900000 }, { key: 'COD', label: 'COD', value: 100000 }]);
    expect(paymentTable(b).rows[1]).toMatchObject({ label: 'COD', orders: 2, revenue: 100000 });
  });
  it('turns top products into bars with the quantity as secondary text', () => {
    const items = topProductItems([{ productName: 'Áo thun', quantity: 12, revenue: 2400000, profit: null }]);
    expect(items[0]).toMatchObject({ label: 'Áo thun', value: 2400000, secondary: 'Đã bán 12' });
  });
});

describe('isEmptyPeriod', () => {
  it('is empty with no created and no paid orders', () => {
    expect(isEmptyPeriod({ kpis: { orders: { value: 0 }, paidOrders: { value: 0 } } })).toBe(true);
    expect(isEmptyPeriod({ kpis: { orders: { value: 0 }, paidOrders: { value: 1 } } })).toBe(false);
    expect(isEmptyPeriod({ kpis: { orders: { value: 2 }, paidOrders: { value: 0 } } })).toBe(false);
    expect(isEmptyPeriod(null)).toBe(false);
  });
});
