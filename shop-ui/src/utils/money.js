const formatter = new Intl.NumberFormat('vi-VN');

export function formatVnd(amount) {
  return `${formatter.format(amount ?? 0)} ₫`;
}
