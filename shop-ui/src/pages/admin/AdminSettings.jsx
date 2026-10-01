import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { api } from '../../api/client.js';
import Badge from '../../components/ui/Badge.jsx';
import Button from '../../components/ui/Button.jsx';
import EmptyState from '../../components/ui/EmptyState.jsx';
import Field from '../../components/ui/Field.jsx';
import { Skeleton } from '../../components/ui/Skeleton.jsx';
import { useToast } from '../../components/ui/Toast.jsx';
import { AlertIcon, CheckIcon } from '../../components/ui/icons.jsx';
import { useGhnAddress } from '../../hooks/useGhnAddress.js';
import { MODE_IDS, findOption, listStatus, selectionReducer, toOptions } from '../../utils/address.js';
import {
  NOTE, SETTINGS_FIELD_ORDER, buildSettingsPayload, carrierRows, isSettingsDirty, settingsErrors, settingsMode,
  settingsSnapshot, settingsToState, withSavedOption,
} from '../../utils/settings.js';
import AdminPageHeader from './AdminPageHeader.jsx';

const LEAVE_MESSAGE = 'Bạn có thay đổi chưa lưu. Rời trang và bỏ các thay đổi này?';

/** Warns before losing edits: closing/reloading the tab, or following any in-app link (the router has no blocker API). */
function useLeaveGuard(active) {
  useEffect(() => {
    if (!active) return undefined;
    const onBeforeUnload = (e) => { e.preventDefault(); e.returnValue = ''; };
    const onClick = (e) => {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      const link = e.target instanceof Element ? e.target.closest('a[href]') : null;
      if (!link || link.target === '_blank' || link.origin !== window.location.origin) return;
      if (link.pathname === window.location.pathname) return;
      if (!window.confirm(LEAVE_MESSAGE)) { e.preventDefault(); e.stopPropagation(); }
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    document.addEventListener('click', onClick, true);
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
      document.removeEventListener('click', onClick, true);
    };
  }, [active]);
}

/** Alert + retry under a select whose list could not be loaded. */
function ListError({ what, onRetry }) {
  return (
    <span className="ad-loaderr" role="alert">
      <span>Không tải được {what}.</span>
      <button type="button" className="ad-loaderr__retry" onClick={onRetry}>Thử lại</button>
    </span>
  );
}

function SettingsSkeleton() {
  return (
    <div role="status" aria-label="Đang tải cài đặt cửa hàng" className="ad-set">
      <div className="ad-set__main" aria-hidden="true">
        <Skeleton height={220} radius={20} />
        <Skeleton height={340} radius={20} />
      </div>
      <Skeleton height={200} radius={20} />
    </div>
  );
}

function CarrierCard({ carriers }) {
  return (
    <aside className="ad-card ad-set__card ad-set__side" aria-labelledby="ad-set-carriers">
      <h2 id="ad-set-carriers" className="ad-set__title">Đơn vị vận chuyển</h2>
      <ul className="ad-carriers">
        {carrierRows(carriers).map((c) => (
          <li key={c.key} className="ad-carrier">
            <div className="ad-carrier__row">
              <strong>{c.label}</strong>
              <Badge tone={c.enabled ? 'success' : 'neutral'} dot>{c.enabled ? 'Đang bật' : 'Chưa bật'}</Badge>
            </div>
            {c.detail && <p className="ad-muted">{c.detail}</p>}
            {c.hint && <p className="ad-muted">{c.hint}</p>}
          </li>
        ))}
      </ul>
      <p className="ad-set__note">{NOTE}</p>
    </aside>
  );
}

function SettingsForm({ settings, onSaved }) {
  const { toast } = useToast();
  const mode = settingsMode(settings);
  const idsMode = mode === MODE_IDS;
  const initial = useMemo(() => settingsToState(settings), [settings]);
  const [form, setForm] = useState(initial.form);
  const [selections, dispatchSel] = useReducer(selectionReducer, initial.selections);
  const [baseline, setBaseline] = useState(() => settingsSnapshot({ mode, ...initial }));
  const [touched, setTouched] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const [provinces, setProvinces] = useState([]); // fixed table, TEXT mode only
  const [provincesError, setProvincesError] = useState(false);
  const [provincesAttempt, setProvincesAttempt] = useState(0);

  const ghn = useGhnAddress({ enabled: idsMode, provinceId: selections.province?.id, districtId: selections.district?.id });

  useEffect(() => {
    if (idsMode) return undefined;
    let ignore = false;
    api('GET', '/shipping/provinces', undefined, { auth: false })
      .then((list) => { if (!ignore) { setProvinces(list); setProvincesError(false); } })
      .catch(() => { if (!ignore) setProvincesError(true); });
    return () => { ignore = true; };
  }, [idsMode, provincesAttempt]);

  const current = { mode, form, selections };
  const dirty = isSettingsDirty(baseline, current);
  useLeaveGuard(dirty);
  const errors = useMemo(() => settingsErrors({ mode, form, selections }), [mode, form, selections]);
  const shown = (key) => (submitted || touched[key] ? errors[key] : undefined);
  const set = (key) => (e) => { const { value } = e.target; setForm((f) => ({ ...f, [key]: value })); };
  const blur = (key) => () => setTouched((t) => ({ ...t, [key]: true }));

  const provinceOptions = useMemo(
    () => withSavedOption(toOptions(ghn.provinces.items, 'id'), initial.selections.province, 'id'),
    [ghn.provinces.items, initial.selections.province],
  );
  const districtOptions = useMemo(() => {
    const saved = selections.province?.id === initial.selections.province?.id ? initial.selections.district : null;
    return withSavedOption(toOptions(ghn.districts.items, 'id'), saved, 'id');
  }, [ghn.districts.items, selections.province, initial.selections]);
  const wardOptions = useMemo(() => {
    const sameParent = selections.district?.id === initial.selections.district?.id;
    return withSavedOption(toOptions(ghn.wards.items, 'code'), sameParent ? initial.selections.ward : null, 'code');
  }, [ghn.wards.items, selections.district, initial.selections]);
  const tableProvinces = useMemo(() => {
    const names = provinces.map((p) => p.province);
    return form.province && !names.includes(form.province) ? [form.province, ...names] : names;
  }, [provinces, form.province]);

  function focusField(key) {
    const el = document.getElementById(`ss-${key}`);
    if (el && !el.disabled) el.focus();
    else document.querySelector('.ad-set .ad-loaderr__retry')?.focus();
  }

  async function submit(e) {
    e.preventDefault();
    if (saving || inFlight.current) return;
    setError('');
    setSubmitted(true);
    const first = SETTINGS_FIELD_ORDER.find((key) => errors[key]);
    if (first) { focusField(first); return; }
    inFlight.current = true;
    setSaving(true);
    try {
      const saved = await api('PUT', '/admin/settings/shop', buildSettingsPayload(current));
      onSaved(saved);
      toast('Đã lưu cài đặt cửa hàng', { tone: 'success' });
    } catch (err) {
      setError(err.message || 'Không lưu được cài đặt cửa hàng');
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  // `settings` changes only after a successful save: adopt the server's (re-resolved) values as the new baseline.
  const adopted = useRef(settings);
  useEffect(() => {
    if (adopted.current === settings) return;
    adopted.current = settings;
    const next = settingsToState(settings);
    setForm(next.form);
    dispatchSel({ type: 'province', option: next.selections.province });
    if (next.selections.district) dispatchSel({ type: 'district', option: next.selections.district });
    if (next.selections.ward) dispatchSel({ type: 'ward', option: next.selections.ward });
    setBaseline(settingsSnapshot({ mode, ...next }));
    setSubmitted(false);
    setTouched({});
  }, [settings, mode]);

  const loading = (list) => list.status === 'loading';
  const districtsDown = listStatus(ghn.districts) === 'error';
  const wardsDown = listStatus(ghn.wards) === 'error';

  return (
    <form className="ad-set" onSubmit={submit} noValidate aria-label="Cài đặt cửa hàng">
      <div className="ad-set__main">
        {error && <p className="ad-alert" role="alert"><AlertIcon size={18} /><span>{error}</span></p>}

        <section className="ad-card ad-set__card" aria-labelledby="ad-set-info">
          <h2 id="ad-set-info" className="ad-set__title">Thông tin cửa hàng</h2>
          <div className="ad-set__fields">
            <Field
              id="ss-shopName" label="Tên cửa hàng" value={form.shopName} onChange={set('shopName')} onBlur={blur('shopName')}
              error={shown('shopName')} maxLength={100} autoComplete="organization" required placeholder="Quini Bear"
            />
            <Field
              id="ss-phone" label="Số điện thoại" optional value={form.phone} onChange={set('phone')} onBlur={blur('phone')}
              error={shown('phone')} inputMode="tel" autoComplete="tel" placeholder="0901234567"
            />
          </div>
        </section>

        <section className="ad-card ad-set__card" aria-labelledby="ad-set-pickup">
          <h2 id="ad-set-pickup" className="ad-set__title">Địa chỉ lấy hàng</h2>
          <div className="ad-set__fields">
            {idsMode ? (
              <>
                <Field
                  as="select" id="ss-province" label="Tỉnh/Thành" required
                  value={selections.province ? String(selections.province.id) : ''}
                  disabled={loading(ghn.provinces) || (ghn.provinces.status === 'error' && !selections.province)}
                  aria-busy={loading(ghn.provinces) ? true : undefined}
                  onChange={(e) => dispatchSel({ type: 'province', option: findOption(provinceOptions, e.target.value) })}
                  onBlur={blur('province')}
                  error={ghn.provinces.status === 'error' ? undefined : shown('province')}
                  hint={ghn.provinces.status === 'error' ? <ListError what="danh sách tỉnh/thành" onRetry={ghn.provinces.retry} /> : undefined}
                >
                  <option value="">{loading(ghn.provinces) ? 'Đang tải...' : 'Chọn tỉnh/thành'}</option>
                  {provinceOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </Field>
                <Field
                  as="select" id="ss-district" label="Quận/Huyện" required
                  value={selections.district ? String(selections.district.id) : ''}
                  disabled={!selections.province || ghn.districts.status !== 'ready'}
                  aria-busy={loading(ghn.districts) ? true : undefined}
                  onChange={(e) => dispatchSel({ type: 'district', option: findOption(districtOptions, e.target.value) })}
                  onBlur={blur('district')}
                  error={districtsDown ? undefined : shown('district')}
                  hint={districtsDown
                    ? <ListError what="danh sách quận/huyện" onRetry={ghn.districts.retry} />
                    : selections.province ? undefined : 'Chọn tỉnh/thành trước'}
                >
                  <option value="">{loading(ghn.districts) ? 'Đang tải...' : 'Chọn quận/huyện'}</option>
                  {districtOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </Field>
                <Field
                  className="ad-set__wide" as="select" id="ss-ward" label="Phường/Xã" required
                  value={selections.ward ? selections.ward.code : ''}
                  disabled={!selections.district || ghn.wards.status !== 'ready'}
                  aria-busy={loading(ghn.wards) ? true : undefined}
                  onChange={(e) => dispatchSel({ type: 'ward', option: findOption(wardOptions, e.target.value) })}
                  onBlur={blur('ward')}
                  error={wardsDown ? undefined : shown('ward')}
                  hint={wardsDown
                    ? <ListError what="danh sách phường/xã" onRetry={ghn.wards.retry} />
                    : selections.district ? undefined : 'Chọn quận/huyện trước'}
                >
                  <option value="">{loading(ghn.wards) ? 'Đang tải...' : 'Chọn phường/xã'}</option>
                  {wardOptions.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </Field>
              </>
            ) : (
              <>
                <Field
                  as="select" id="ss-province" label="Tỉnh/Thành" required value={form.province} onChange={set('province')}
                  onBlur={blur('province')}
                  error={provincesError ? undefined : shown('province')}
                  hint={provincesError ? <ListError what="danh sách tỉnh/thành" onRetry={() => setProvincesAttempt((n) => n + 1)} /> : undefined}
                >
                  <option value="">Chọn tỉnh/thành</option>
                  {tableProvinces.map((name) => <option key={name} value={name}>{name}</option>)}
                </Field>
                <Field
                  id="ss-district" label="Quận/Huyện" optional value={form.district} onChange={set('district')}
                  maxLength={100} autoComplete="off" placeholder="Ví dụ: Quận Cầu Giấy"
                />
                <Field
                  className="ad-set__wide" id="ss-ward" label="Phường/Xã" required value={form.ward} onChange={set('ward')}
                  onBlur={blur('ward')} error={shown('ward')} maxLength={100} autoComplete="off" placeholder="Ví dụ: Phường Dịch Vọng"
                />
              </>
            )}
            <Field
              className="ad-set__wide" id="ss-address" label="Số nhà, tên đường" optional value={form.address}
              onChange={set('address')} onBlur={blur('address')} error={shown('address')} maxLength={300}
              autoComplete="off" placeholder="Số nhà, tên đường"
            />
          </div>
        </section>

        <div className="ad-foot ad-set__foot">
          <Button type="submit" loading={saving} iconLeft={<CheckIcon size={18} />}>Lưu</Button>
          {dirty && !saving && <span className="ad-dirty" role="status">Chưa lưu</span>}
          {!dirty && settings.updatedAt && (
            <span className="ad-muted">
              Cập nhật lần cuối {new Date(settings.updatedAt).toLocaleString('vi-VN')}{settings.updatedBy ? ` bởi ${settings.updatedBy}` : ''}
            </span>
          )}
        </div>
      </div>
      <CarrierCard carriers={settings.carriers} />
    </form>
  );
}

export default function AdminSettings() {
  const [settings, setSettings] = useState(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let ignore = false;
    api('GET', '/admin/settings/shop')
      .then((s) => { if (!ignore) { setSettings(s); setError(''); } })
      .catch((e) => { if (!ignore) setError(e.message || 'Có lỗi xảy ra'); });
    return () => { ignore = true; };
  }, [attempt]);

  const retry = useCallback(() => { setError(''); setAttempt((n) => n + 1); }, []);

  let body;
  if (error && !settings) {
    body = (
      <EmptyState
        role="alert"
        tone="danger"
        icon={<AlertIcon size={26} />}
        title="Không tải được cài đặt cửa hàng"
        action={<Button variant="dark" onClick={retry}>Thử lại</Button>}
      >
        {error}
      </EmptyState>
    );
  } else if (!settings) {
    body = <SettingsSkeleton />;
  } else {
    body = <SettingsForm settings={settings} onSaved={setSettings} />;
  }

  return (
    <>
      <AdminPageHeader title="Cài đặt cửa hàng" description="Thông tin cửa hàng và địa chỉ lấy hàng dùng để tính phí vận chuyển." />
      {body}
    </>
  );
}
