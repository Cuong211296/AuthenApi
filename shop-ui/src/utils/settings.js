import { MODE_IDS, MODE_TEXT } from './address.js';
import { isValidPhone } from './checkout.js';

/**
 * Pure helpers for the admin shop settings page (`/admin/settings`).
 * The form holds text fields; in GHN_IDS mode the pickup location is a selection `{province, district, ward}`
 * (province/district `{id, name}`, ward `{code, name}`), in TEXT mode the location lives in the form text fields.
 */

export const EMPTY_SETTINGS_FORM = { shopName: '', phone: '', address: '', province: '', district: '', ward: '' };
export const EMPTY_PICKUP_SELECTION = { province: null, district: null, ward: null };

export const NOTE = 'Phí vận chuyển được tính từ địa chỉ này. Thay đổi có hiệu lực ngay cho các đơn mới.';

const text = (v) => String(v ?? '').trim();
const orNull = (v) => text(v) || null;
/** Phone as the server expects it: separators typed inside the number are dropped. */
export const normalizePhone = (v) => String(v ?? '').replace(/[\s.-]/g, '');

/** The page mode from the server's `addressMode`: anything but GHN_IDS is the text address. */
export const settingsMode = (settings) => (settings?.addressMode === MODE_IDS ? MODE_IDS : MODE_TEXT);

/**
 * Initial form + selection for the saved settings. In GHN_IDS mode the saved ids become the pre-selected options (the
 * saved names are kept so the selects can show them even while GHN is unreachable); a partial pickup selects nothing
 * below the first missing id. In TEXT mode the saved names fill the text fields.
 */
export function settingsToState(settings) {
  const pickup = settings?.pickup ?? {};
  const form = {
    shopName: settings?.shopName ?? '',
    phone: settings?.phone ?? '',
    address: pickup.address ?? '',
    province: pickup.provinceName ?? '',
    district: pickup.districtName ?? '',
    ward: pickup.wardName ?? '',
  };
  let selections = EMPTY_PICKUP_SELECTION;
  if (settingsMode(settings) === MODE_IDS && pickup.provinceId != null) {
    const province = { id: pickup.provinceId, name: pickup.provinceName || String(pickup.provinceId) };
    const district = pickup.districtId != null
      ? { id: pickup.districtId, name: pickup.districtName || String(pickup.districtId) } : null;
    const ward = district && pickup.wardCode
      ? { code: pickup.wardCode, name: pickup.wardName || pickup.wardCode } : null;
    selections = { province, district, ward };
  }
  return { form, selections };
}

/**
 * PUT body. GHN_IDS: ids (+ display names, the server re-resolves them). TEXT: names only, ALL ids are dropped
 * because the server rejects ids when GHN is not enabled.
 */
export function buildSettingsPayload({ mode, form, selections }) {
  const base = { shopName: text(form?.shopName), phone: orNull(normalizePhone(form?.phone)) };
  const address = orNull(form?.address);
  if (mode === MODE_IDS) {
    const s = selections ?? EMPTY_PICKUP_SELECTION;
    return {
      ...base,
      pickup: {
        provinceId: s.province?.id ?? null,
        districtId: s.district?.id ?? null,
        wardCode: s.ward?.code ?? null,
        provinceName: s.province?.name ?? null,
        districtName: s.district?.name ?? null,
        wardName: s.ward?.name ?? null,
        address,
      },
    };
  }
  return {
    ...base,
    pickup: {
      provinceName: orNull(form?.province),
      districtName: orNull(form?.district),
      wardName: orNull(form?.ward),
      address,
    },
  };
}

/** Comparable snapshot of what would be saved (selection reduced to ids so renamed labels never count as edits). */
export function settingsSnapshot({ mode, form, selections }) {
  const p = buildSettingsPayload({ mode, form, selections }).pickup;
  const head = [text(form?.shopName), normalizePhone(form?.phone)];
  if (mode === MODE_IDS) return JSON.stringify([...head, p.provinceId, p.districtId, p.wardCode, p.address]);
  return JSON.stringify([...head, p.provinceName, p.districtName, p.wardName, p.address]);
}

/** True when the current edit differs from the baseline snapshot (see settingsSnapshot). */
export function isSettingsDirty(baseline, current) {
  if (baseline == null || !current) return false;
  return baseline !== settingsSnapshot(current);
}

/** Inline messages keyed by field (shopName, phone, province, district, ward, address); only invalid fields are present. */
export function settingsErrors({ mode, form, selections }) {
  const errors = {};
  if (!text(form?.shopName)) errors.shopName = 'Vui lòng nhập tên cửa hàng';
  else if (text(form.shopName).length > 100) errors.shopName = 'Tên cửa hàng tối đa 100 ký tự';
  const phone = normalizePhone(form?.phone);
  if (phone && !isValidPhone(phone)) errors.phone = 'Số điện thoại không hợp lệ (ví dụ 0901234567)';
  if (mode === MODE_IDS) {
    if (!selections?.province) errors.province = 'Hãy chọn tỉnh/thành';
    if (!selections?.district) errors.district = 'Hãy chọn quận/huyện';
    if (!selections?.ward) errors.ward = 'Hãy chọn phường/xã';
  } else {
    if (!text(form?.province)) errors.province = 'Hãy chọn tỉnh/thành';
    if (!text(form?.ward)) errors.ward = 'Vui lòng nhập phường/xã';
  }
  if (text(form?.address).length > 300) errors.address = 'Địa chỉ tối đa 300 ký tự';
  return errors;
}

export const SETTINGS_FIELD_ORDER = ['shopName', 'phone', 'province', 'district', 'ward', 'address'];

/** Options for a select that always contain the saved value, even when its list has not loaded (or is unreachable). */
export function withSavedOption(options, saved, key) {
  if (!saved) return options;
  const value = String(saved[key]);
  return options.some((o) => o.value === value) ? options : [{ value, label: saved.name, item: saved }, ...options];
}

export const CARRIER_STATE_LABEL = { on: 'Đang bật', off: 'Đã tắt', missing: 'Chưa cấu hình' };
export const ALL_OFF_NOTE = 'Cả hai đơn vị đều đang tắt hoặc chưa cấu hình, hệ thống dùng bảng phí cố định theo tỉnh.';

function carrierRow(key, label, carrier, detail, missingHint) {
  const configured = Boolean(carrier.configured);
  const on = configured && carrier.enabled !== false; // a missing switch value means on
  return { key, label, configured, on, state: !configured ? 'missing' : on ? 'on' : 'off', detail, hint: configured ? '' : missingHint };
}

/** Status card rows for the carriers ({key, label, configured, on, state, detail, hint}). */
export function carrierRows(carriers) {
  const ghn = carriers?.ghn ?? {};
  const ghtk = carriers?.ghtk ?? {};
  return [
    carrierRow('ghn', 'GHN', ghn, ghn.configured && ghn.shopId ? `Shop ID ${ghn.shopId}` : '', 'Thêm GHN_SHOP_ID vào .env để bật GHN'),
    carrierRow('ghtk', 'GHTK', ghtk, '', 'Thêm GHTK_TOKEN vào .env để bật GHTK'),
  ];
}

/** Body of PUT /admin/settings/carriers after flipping one carrier (the other keeps its stored switch). */
export function carrierSwitchBody(carriers, key, on) {
  return { ghn: carriers?.ghn?.enabled !== false, ghtk: carriers?.ghtk?.enabled !== false, [key]: on };
}

/** True when no carrier can quote (every one is off or not configured), so the fixed province table is used. */
export const allCarriersOff = (carriers) => carrierRows(carriers).every((r) => !r.on);
