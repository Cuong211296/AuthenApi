import {
  EMPTY_PICKUP_SELECTION, buildSettingsPayload, CARRIER_STATE_LABEL, allCarriersOff, carrierRows, carrierSwitchBody, isSettingsDirty, settingsErrors, settingsMode, settingsSnapshot,
  settingsToState, withSavedOption,
} from './settings.js';

const saved = {
  shopName: 'Quini Bear', phone: '0901234567', addressMode: 'GHN_IDS',
  pickup: { provinceId: 201, districtId: 1490, wardCode: '1A0101', provinceName: 'Hà Nội', districtName: 'Cầu Giấy', wardName: 'Dịch Vọng', address: '1 Xuân Thủy' },
};
const textSaved = { ...saved, addressMode: 'TEXT', pickup: { provinceName: 'Hà Nội', districtName: null, wardName: 'Dịch Vọng', address: '1 Xuân Thủy' } };

describe('settingsToState', () => {
  it('pre-selects the saved ids with their saved names in GHN_IDS mode', () => {
    const { form, selections } = settingsToState(saved);
    expect(selections).toEqual({
      province: { id: 201, name: 'Hà Nội' }, district: { id: 1490, name: 'Cầu Giấy' }, ward: { code: '1A0101', name: 'Dịch Vọng' },
    });
    expect(form).toMatchObject({ shopName: 'Quini Bear', phone: '0901234567', address: '1 Xuân Thủy' });
  });
  it('selects nothing when no pickup is saved and falls back to the id when a name is missing', () => {
    expect(settingsToState({ addressMode: 'GHN_IDS', pickup: {} }).selections).toEqual(EMPTY_PICKUP_SELECTION);
    expect(settingsToState(null).form.shopName).toBe('');
    const s = settingsToState({ addressMode: 'GHN_IDS', pickup: { provinceId: 5, districtId: 6, wardCode: 'X' } }).selections;
    expect(s.province.name).toBe('5');
    expect(s.ward.name).toBe('X');
  });
  it('does not select a ward without its district; TEXT mode fills the text fields', () => {
    const s = settingsToState({ addressMode: 'GHN_IDS', pickup: { provinceId: 1, wardCode: 'W' } }).selections;
    expect(s.district).toBeNull();
    expect(s.ward).toBeNull();
    const t = settingsToState(textSaved);
    expect(t.selections).toEqual(EMPTY_PICKUP_SELECTION);
    expect(t.form).toMatchObject({ province: 'Hà Nội', district: '', ward: 'Dịch Vọng' });
  });
});

describe('buildSettingsPayload', () => {
  it('GHN_IDS sends ids and names', () => {
    const { form, selections } = settingsToState(saved);
    expect(buildSettingsPayload({ mode: 'GHN_IDS', form, selections })).toEqual({
      shopName: 'Quini Bear', phone: '0901234567',
      pickup: { provinceId: 201, districtId: 1490, wardCode: '1A0101', provinceName: 'Hà Nội', districtName: 'Cầu Giấy', wardName: 'Dịch Vọng', address: '1 Xuân Thủy' },
    });
  });
  it('TEXT drops every id, even stale selections', () => {
    const { form } = settingsToState(textSaved);
    const stale = { province: { id: 1, name: 'x' }, district: { id: 2, name: 'y' }, ward: { code: 'z', name: 'w' } };
    const { pickup } = buildSettingsPayload({ mode: 'TEXT', form, selections: stale });
    expect(pickup).toEqual({ provinceName: 'Hà Nội', districtName: null, wardName: 'Dịch Vọng', address: '1 Xuân Thủy' });
    expect('provinceId' in pickup || 'districtId' in pickup || 'wardCode' in pickup).toBe(false);
  });
  it('trims, strips phone separators and sends null for empty optional fields', () => {
    const p = buildSettingsPayload({ mode: 'TEXT', form: { shopName: ' A ', phone: '', address: '  ', province: 'Hà Nội', district: ' ', ward: ' P1 ' }, selections: null });
    expect(p).toEqual({ shopName: 'A', phone: null, pickup: { provinceName: 'Hà Nội', districtName: null, wardName: 'P1', address: null } });
    expect(buildSettingsPayload({ mode: 'TEXT', form: { shopName: 'A', phone: '0901 234.567' } }).phone).toBe('0901234567');
  });
});

describe('dirty check', () => {
  const state = () => settingsToState(saved);
  const base = settingsSnapshot({ mode: 'GHN_IDS', ...state() });
  it('is clean for the loaded values, whitespace-only edits and renamed labels', () => {
    const { form, selections } = state();
    expect(isSettingsDirty(base, { mode: 'GHN_IDS', form, selections })).toBe(false);
    expect(isSettingsDirty(base, { mode: 'GHN_IDS', form: { ...form, shopName: 'Quini Bear  ' }, selections })).toBe(false);
    expect(isSettingsDirty(base, { mode: 'GHN_IDS', form, selections: { ...selections, ward: { code: '1A0101', name: 'Other label' } } })).toBe(false);
  });
  it('is dirty after changing a field or a selection', () => {
    const { form, selections } = state();
    expect(isSettingsDirty(base, { mode: 'GHN_IDS', form: { ...form, phone: '0912345678' }, selections })).toBe(true);
    expect(isSettingsDirty(base, { mode: 'GHN_IDS', form, selections: { ...selections, ward: null } })).toBe(true);
  });
  it('has nothing to compare before the baseline exists', () => {
    expect(isSettingsDirty(null, { mode: 'TEXT', form: {} })).toBe(false);
  });
  it('TEXT mode compares the text fields', () => {
    const { form } = settingsToState(textSaved);
    const b = settingsSnapshot({ mode: 'TEXT', form });
    expect(isSettingsDirty(b, { mode: 'TEXT', form })).toBe(false);
    expect(isSettingsDirty(b, { mode: 'TEXT', form: { ...form, ward: 'Phường 2' } })).toBe(true);
  });
});

describe('settingsErrors', () => {
  it('requires the shop name and a complete GHN selection', () => {
    expect(settingsErrors({ mode: 'GHN_IDS', form: { shopName: ' ' }, selections: EMPTY_PICKUP_SELECTION })).toEqual({
      shopName: 'Vui lòng nhập tên cửa hàng', province: 'Hãy chọn tỉnh/thành', district: 'Hãy chọn quận/huyện', ward: 'Hãy chọn phường/xã',
    });
    const { form, selections } = settingsToState(saved);
    expect(settingsErrors({ mode: 'GHN_IDS', form, selections })).toEqual({});
  });
  it('TEXT mode needs province and ward text; district is optional', () => {
    expect(Object.keys(settingsErrors({ mode: 'TEXT', form: { shopName: 'A' } }))).toEqual(['province', 'ward']);
    expect(settingsErrors({ mode: 'TEXT', form: { shopName: 'A', province: 'Hà Nội', ward: 'P1' } })).toEqual({});
  });
  it('validates the phone only when present', () => {
    const ok = { mode: 'TEXT', form: { shopName: 'A', province: 'H', ward: 'W' } };
    expect(settingsErrors({ ...ok, form: { ...ok.form, phone: '' } }).phone).toBeUndefined();
    expect(settingsErrors({ ...ok, form: { ...ok.form, phone: '0901 234 567' } }).phone).toBeUndefined();
    expect(settingsErrors({ ...ok, form: { ...ok.form, phone: '12345' } }).phone).toMatch(/không hợp lệ/);
  });
  it('limits lengths', () => {
    const e = settingsErrors({ mode: 'TEXT', form: { shopName: 'a'.repeat(101), province: 'H', ward: 'W', address: 'a'.repeat(301) } });
    expect(e.shopName).toBeDefined();
    expect(e.address).toBeDefined();
  });
});

describe('misc', () => {
  it('settingsMode defaults to TEXT', () => {
    expect(settingsMode({ addressMode: 'GHN_IDS' })).toBe('GHN_IDS');
    expect(settingsMode({})).toBe('TEXT');
    expect(settingsMode(null)).toBe('TEXT');
  });
  it('withSavedOption keeps the saved option when the list lacks it', () => {
    const opts = [{ value: '2', label: 'B', item: { id: 2, name: 'B' } }];
    expect(withSavedOption(opts, { id: 1, name: 'A' }, 'id').map((o) => o.value)).toEqual(['1', '2']);
    expect(withSavedOption(opts, { id: 2, name: 'B' }, 'id')).toBe(opts);
    expect(withSavedOption(opts, null, 'id')).toBe(opts);
  });
  it('carrierRows tells missing, on and off apart', () => {
    const rows = carrierRows({
      ghn: { configured: true, enabled: false, shopId: '123' },
      ghtk: { configured: false, enabled: true },
    });
    expect(rows[0]).toMatchObject({ key: 'ghn', configured: true, on: false, state: 'off', detail: 'Shop ID 123', hint: '' });
    expect(rows[1]).toMatchObject({
      key: 'ghtk', configured: false, on: false, state: 'missing', hint: 'Thêm GHTK_TOKEN vào .env để bật GHTK',
    });
    expect(carrierRows({ ghn: { configured: false, enabled: true } })[0].hint).toBe('Thêm GHN_SHOP_ID vào .env để bật GHN');
    const on = carrierRows({ ghn: { configured: true, enabled: true, shopId: null }, ghtk: { configured: true } });
    expect(on[0]).toMatchObject({ state: 'on', on: true, detail: '' });
    expect(on[1]).toMatchObject({ state: 'on', on: true }); // a missing switch value means on
    expect(CARRIER_STATE_LABEL).toEqual({ on: 'Đang bật', off: 'Đã tắt', missing: 'Chưa cấu hình' });
  });

  it('carrierSwitchBody flips one carrier and keeps the other stored value', () => {
    const carriers = { ghn: { configured: true, enabled: true }, ghtk: { configured: true, enabled: false } };
    expect(carrierSwitchBody(carriers, 'ghn', false)).toEqual({ ghn: false, ghtk: false });
    expect(carrierSwitchBody(carriers, 'ghtk', true)).toEqual({ ghn: true, ghtk: true });
    expect(carrierSwitchBody(null, 'ghn', false)).toEqual({ ghn: false, ghtk: true });
  });

  it('allCarriersOff is true when nothing can quote (off or not configured)', () => {
    expect(allCarriersOff({ ghn: { configured: true, enabled: false }, ghtk: { configured: false, enabled: true } })).toBe(true);
    expect(allCarriersOff({ ghn: { configured: true, enabled: true }, ghtk: { configured: false } })).toBe(false);
  });
});
