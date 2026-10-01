/**
 * Pure helpers for the checkout address block.
 * Two modes: 'GHN_IDS' (dependent selects fed by GHN master data, the order carries ids) and 'TEXT' (province select
 * from the fixed table plus a free-text ward). A selection holds the chosen options: province/district `{id, name}`,
 * ward `{code, name}`, or null.
 */

export const MODE_IDS = 'GHN_IDS';
export const MODE_TEXT = 'TEXT';
export const DEFAULT_CONFIG = { provider: 'TABLE', addressMode: MODE_TEXT };
export const EMPTY_SELECTION = { province: null, district: null, ward: null };

/** GHN id selects need both the config flag and a province list that actually loaded; otherwise plain text fields. */
export function addressModeFromConfig(config, provincesFailed = false) {
  return config?.addressMode === MODE_IDS && !provincesFailed ? MODE_IDS : MODE_TEXT;
}

/** Selecting a province clears district and ward, selecting a district clears the ward; `option` may be null to clear. */
export function selectionReducer(state, action) {
  const option = action.option ?? null;
  switch (action.type) {
    case 'province': return { province: option, district: null, ward: null };
    case 'district': return { ...state, district: option, ward: null };
    case 'ward': return { ...state, ward: option };
    case 'reset': return EMPTY_SELECTION;
    default: return state;
  }
}

const text = (v) => String(v ?? '').trim();

/**
 * The address part of a quote body / order payload. Ids mode sends the three ids plus the display names (the server
 * resolves names from the ids); text mode sends province, ward and address only.
 */
export function buildAddressPayload({ mode, form, selections }) {
  const address = text(form?.address);
  if (mode === MODE_IDS) {
    const s = selections ?? EMPTY_SELECTION;
    return {
      provinceId: s.province?.id ?? null,
      districtId: s.district?.id ?? null,
      wardCode: s.ward?.code ?? null,
      province: s.province?.name ?? '',
      district: s.district?.name ?? '',
      ward: s.ward?.name ?? '',
      address,
    };
  }
  return { province: text(form?.province), ward: text(form?.ward), address };
}

/** True once every location field required by the mode is filled in (the street address is optional for quotes). */
export function isAddressComplete(mode, form, selections) {
  if (mode === MODE_IDS) return Boolean(selections?.province && selections?.district && selections?.ward);
  return Boolean(text(form?.province) && text(form?.ward));
}

const collator = new Intl.Collator('vi', { numeric: true, sensitivity: 'base' });

/** Copy sorted by Vietnamese name ("Phường 2" before "Phường 10"); ids/codes stay exactly as given. */
export function sortByName(list) {
  return [...(list ?? [])].sort((a, b) => collator.compare(a.name ?? '', b.name ?? ''));
}

/** `<option>` data for a list of {id|code, name}: the value is the stringified key, `item` is the original entry. */
export function toOptions(list, key) {
  return sortByName(list).map((item) => ({ value: String(item[key]), label: item.name, item }));
}

/** The original entry for a select's string value (or null when empty/unknown). */
export function findOption(options, value) {
  return options.find((o) => o.value === String(value))?.item ?? null;
}
