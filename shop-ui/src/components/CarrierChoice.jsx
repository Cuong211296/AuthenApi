import RadioCards from './ui/RadioCards.jsx';
import { formatVnd } from '../utils/money.js';
import { CARRIER_NAME } from '../utils/shipping.js';

/** Radio cards for the live carriers of a quote; renders nothing unless there are two or more (cheapest first). */
export default function CarrierChoice({ options, value, onChange, disabled = false }) {
  if (options.length < 2) return null;
  const cards = options.map((o, i) => ({
    value: o.source,
    icon: <strong className="co-carrier__abbr">{CARRIER_NAME[o.source]}</strong>,
    title: CARRIER_NAME[o.source],
    description: `${formatVnd(o.fee)}${i === 0 ? ' · Rẻ nhất' : ''}`,
  }));
  return (
    <div className="co-carriers">
      <p className="co-carriers__title">Đơn vị vận chuyển</p>
      <RadioCards label="Đơn vị vận chuyển" value={value} onChange={onChange} options={cards} disabled={disabled} />
    </div>
  );
}
