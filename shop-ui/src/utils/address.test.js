import {
  EMPTY_SELECTION, addressModeFromConfig, buildAddressPayload, findOption, isAddressComplete, selectionReducer, sortByName, toOptions,
} from './address.js';

const hn = { id: 201, name: 'Hà Nội' };
const dist = { id: 1490, name: 'Quận Cầu Giấy' };
const ward = { code: '1A0101', name: 'Phường Dịch Vọng' };
const full = { province: hn, district: dist, ward };

describe('addressModeFromConfig', () => {
  it('uses id selects only for GHN_IDS with a province list that loaded', () => {
    expect(addressModeFromConfig({ provider: 'GHN', addressMode: 'GHN_IDS' }, false)).toBe('GHN_IDS');
    expect(addressModeFromConfig({ provider: 'GHN', addressMode: 'GHN_IDS' }, true)).toBe('TEXT');
    expect(addressModeFromConfig({ provider: 'GHTK', addressMode: 'TEXT' }, false)).toBe('TEXT');
    expect(addressModeFromConfig(null)).toBe('TEXT');
    expect(addressModeFromConfig(undefined, true)).toBe('TEXT');
  });
});

describe('selectionReducer', () => {
  it('selecting a province resets district and ward', () => {
    expect(selectionReducer(full, { type: 'province', option: { id: 202, name: 'Hồ Chí Minh' } }))
      .toEqual({ province: { id: 202, name: 'Hồ Chí Minh' }, district: null, ward: null });
  });
  it('selecting a district resets the ward only', () => {
    expect(selectionReducer(full, { type: 'district', option: { id: 1, name: 'Quận 1' } }))
      .toEqual({ province: hn, district: { id: 1, name: 'Quận 1' }, ward: null });
  });
  it('selecting a ward keeps its parents; clearing the province clears everything below', () => {
    expect(selectionReducer({ ...full, ward: null }, { type: 'ward', option: ward })).toEqual(full);
    expect(selectionReducer(full, { type: 'province', option: null })).toEqual(EMPTY_SELECTION);
    expect(selectionReducer(full, { type: 'reset' })).toEqual(EMPTY_SELECTION);
    expect(selectionReducer(full, { type: 'nope' })).toBe(full);
  });
});

describe('buildAddressPayload', () => {
  const form = { province: 'Hà Nội', ward: 'Phường 1', address: '  12 Xuân Thủy ' };
  it('ids mode sends ids (as given) and display names', () => {
    expect(buildAddressPayload({ mode: 'GHN_IDS', form, selections: full })).toEqual({
      provinceId: 201, districtId: 1490, wardCode: '1A0101',
      province: 'Hà Nội', district: 'Quận Cầu Giấy', ward: 'Phường Dịch Vọng', address: '12 Xuân Thủy',
    });
  });
  it('ids mode ignores the text fields of the form', () => {
    const p = buildAddressPayload({ mode: 'GHN_IDS', form, selections: EMPTY_SELECTION });
    expect(p).toMatchObject({ provinceId: null, districtId: null, wardCode: null, province: '', ward: '' });
  });
  it('text mode sends province, ward and address without ids', () => {
    const p = buildAddressPayload({ mode: 'TEXT', form: { ...form, ward: ' Phường 1 ' }, selections: full });
    expect(p).toEqual({ province: 'Hà Nội', ward: 'Phường 1', address: '12 Xuân Thủy' });
    expect(p).not.toHaveProperty('provinceId');
    expect(p).not.toHaveProperty('district');
  });
});

describe('isAddressComplete', () => {
  it('ids mode needs all three selections', () => {
    expect(isAddressComplete('GHN_IDS', {}, full)).toBe(true);
    expect(isAddressComplete('GHN_IDS', {}, { ...full, ward: null })).toBe(false);
    expect(isAddressComplete('GHN_IDS', { province: 'Hà Nội', ward: 'x' }, EMPTY_SELECTION)).toBe(false);
    expect(isAddressComplete('GHN_IDS', {}, undefined)).toBe(false);
  });
  it('text mode needs a province and a non-blank ward', () => {
    expect(isAddressComplete('TEXT', { province: 'Hà Nội', ward: 'P1' }, EMPTY_SELECTION)).toBe(true);
    expect(isAddressComplete('TEXT', { province: 'Hà Nội', ward: '  ' }, full)).toBe(false);
    expect(isAddressComplete('TEXT', { province: '', ward: 'P1' }, full)).toBe(false);
  });
});

describe('sortByName / toOptions / findOption', () => {
  const list = [{ id: 3, name: 'Hồ Chí Minh' }, { id: 1, name: 'Hà Nội' }, { id: 2, name: 'Bình Dương' }];
  it('sorts by Vietnamese name without mutating the input', () => {
    expect(sortByName(list).map((p) => p.name)).toEqual(['Bình Dương', 'Hà Nội', 'Hồ Chí Minh']);
    expect(list[0].name).toBe('Hồ Chí Minh');
  });
  it('orders numbered wards naturally', () => {
    const wards = [{ code: 'c', name: 'Phường 10' }, { code: 'a', name: 'Phường 2' }, { code: 'b', name: 'Phường 1' }];
    expect(sortByName(wards).map((w) => w.name)).toEqual(['Phường 1', 'Phường 2', 'Phường 10']);
  });
  it('builds string-valued options and finds the original item (numeric id kept)', () => {
    const options = toOptions(list, 'id');
    expect(options[0]).toMatchObject({ value: '2', label: 'Bình Dương' });
    expect(findOption(options, '1')).toEqual({ id: 1, name: 'Hà Nội' });
    expect(findOption(options, '')).toBeNull();
    expect(findOption(options, '99')).toBeNull();
  });
  it('tolerates a missing list', () => {
    expect(sortByName(undefined)).toEqual([]);
  });
});
