import { MODE_IDS, buildAddressPayload } from './address.js';

const PHONE = /^(0|\+84)[0-9]{9}$/;

export function isValidPhone(phone) {
  return PHONE.test(phone);
}

/**
 * Trims text fields (the server validates the raw values) and strips separators typed inside a phone number.
 * `address` = { mode, selections }: in GHN_IDS mode the location comes from the selects (ids + names), never from the
 * text fields; without it (TEXT mode) the province and ward text fields are used as typed.
 */
export function normalizeCheckoutForm(form, address) {
  const note = (form.note || '').trim();
  const location = address?.mode === MODE_IDS ? buildAddressPayload({ mode: MODE_IDS, form, selections: address.selections }) : null;
  return {
    ...form,
    receiverName: form.receiverName.trim(),
    phone: form.phone.replace(/[\s.-]/g, ''),
    email: form.email.trim(),
    ward: (form.ward || '').trim(),
    address: form.address.trim(),
    note: note || null,
    ...location,
  };
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidEmail(email) {
  return EMAIL.test(email);
}

/**
 * Field-level messages for the checkout form, keyed by field name (only invalid fields are present).
 * Validates the normalized values, i.e. exactly what would be sent to the server.
 */
export function checkoutErrors(form, address) {
  const v = normalizeCheckoutForm(form, address);
  const errors = {};
  if (!v.receiverName) errors.receiverName = 'Vui lòng nhập họ tên người nhận';
  if (!v.phone) errors.phone = 'Vui lòng nhập số điện thoại';
  else if (!isValidPhone(v.phone)) errors.phone = 'Số điện thoại không hợp lệ (ví dụ 0901234567)';
  if (!v.email) errors.email = 'Vui lòng nhập email nhận xác nhận đơn';
  else if (!isValidEmail(v.email)) errors.email = 'Email không hợp lệ (ví dụ ten@example.com)';
  if (address?.mode === MODE_IDS) {
    if (!v.provinceId) errors.province = 'Hãy chọn tỉnh/thành';
    if (!v.districtId) errors.district = 'Hãy chọn quận/huyện';
    if (!v.wardCode) errors.ward = 'Hãy chọn phường/xã';
  } else {
    if (!v.province) errors.province = 'Hãy chọn tỉnh/thành';
    if (!v.ward) errors.ward = 'Vui lòng nhập phường/xã';
  }
  if (!v.address) errors.address = 'Vui lòng nhập địa chỉ giao hàng';
  return errors;
}
