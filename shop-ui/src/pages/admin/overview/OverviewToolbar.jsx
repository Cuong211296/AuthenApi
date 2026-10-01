import Field from '../../../components/ui/Field.jsx';
import { RefreshIcon } from '../../../components/ui/icons.jsx';
import { GROUP_OPTIONS, MAX_DAY_GROUP_DAYS, PRESETS, comparisonNote, isGroupByAllowed } from '../../../utils/stats.js';
import Segmented from './Segmented.jsx';

/**
 * Filters of the overview: preset ranges, custom from/to dates (with validation message), group by and the visible
 * comparison note. Controlled; the page owns the state and the rules (see utils/stats.js).
 */
export default function OverviewToolbar({ from, to, preset, groupBy, rangeError, onPreset, onFrom, onTo, onGroupBy, onRefresh, refreshing }) {
  const groupOptions = GROUP_OPTIONS.map((o) => ({ ...o, disabled: !rangeError && !isGroupByAllowed(o.value, from, to) }));
  const dayDisabled = groupOptions[0].disabled;
  return (
    <section className="ov-toolbar" aria-label="Bộ lọc thời gian">
      <div className="ov-toolbar__row">
        <div className="ov-toolbar__group">
          <span className="ov-toolbar__label">Khoảng thời gian</span>
          <Segmented options={PRESETS.map((p) => ({ value: p.key, label: p.label }))} value={preset} onChange={onPreset} label="Khoảng thời gian" />
        </div>
        <div className="ov-toolbar__group">
          <span className="ov-toolbar__label">Nhóm theo</span>
          <Segmented options={groupOptions} value={groupBy} onChange={onGroupBy} label="Nhóm theo" />
        </div>
      </div>
      <div className="ov-toolbar__row ov-toolbar__row--dates">
        <Field
          id="ov-from"
          className="ov-date"
          label="Từ ngày"
          type="date"
          value={from}
          onChange={(e) => onFrom(e.target.value)}
          aria-invalid={rangeError ? true : undefined}
          aria-describedby={rangeError ? 'ov-to-msg' : undefined}
        />
        <Field
          id="ov-to"
          className="ov-date"
          label="Đến ngày"
          type="date"
          value={to}
          error={rangeError}
          onChange={(e) => onTo(e.target.value)}
        />
        <p className="ov-compare">
          <span className="ov-compare__dot" aria-hidden="true" />
          {comparisonNote(from, to)}
        </p>
        <button type="button" className="ov-refresh" onClick={onRefresh} disabled={refreshing}>
          <RefreshIcon size={16} />
          Làm mới
        </button>
      </div>
      {dayDisabled && (
        <p className="ov-toolbar__note">Nhóm theo ngày chỉ dùng được cho khoảng tối đa {MAX_DAY_GROUP_DAYS} ngày.</p>
      )}
    </section>
  );
}
