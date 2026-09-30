import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';

export default function AdminShipping() {
  const [rates, setRates] = useState([]);
  const [fees, setFees] = useState({});
  const [newRate, setNewRate] = useState({ province: '', fee: 35000 });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const apply = (list) => {
    setRates(list);
    setFees(Object.fromEntries(list.map((r) => [r.id, r.fee])));
  };
  const reload = () => api('GET', '/admin/shipping-rates').then(apply);

  useEffect(() => {
    let ignore = false;
    api('GET', '/admin/shipping-rates')
      .then((list) => { if (!ignore) apply(list); })
      .catch((e) => { if (!ignore) setError(e.message); });
    return () => { ignore = true; };
  }, []);

  async function save(rate) {
    setError(''); setMessage('');
    try {
      await api('PUT', `/admin/shipping-rates/${rate.id}`, { province: rate.province, fee: Number(fees[rate.id]) });
      setMessage(`Đã lưu ${rate.province}`);
    } catch (e) { setError(e.message); }
  }

  async function create(e) {
    e.preventDefault();
    setError(''); setMessage('');
    try {
      await api('POST', '/admin/shipping-rates', { province: newRate.province.trim(), fee: Number(newRate.fee) });
      setNewRate({ province: '', fee: 35000 });
      await reload();
    } catch (err) { setError(err.message); }
  }

  return (
    <div className="card">
      <h1>Phí vận chuyển theo tỉnh/thành</h1>
      {error && <p className="alert alert-error">{error}</p>}
      {message && <p className="alert alert-ok">{message}</p>}
      <form className="row" onSubmit={create}>
        <input placeholder="Tỉnh/Thành mới" value={newRate.province} onChange={(e) => setNewRate({ ...newRate, province: e.target.value })} required />
        <input type="number" min="0" value={newRate.fee} onChange={(e) => setNewRate({ ...newRate, fee: e.target.value })} required />
        <button className="btn btn-primary">Thêm</button>
      </form>
      <table>
        <thead><tr><th>Tỉnh/Thành</th><th>Phí (₫)</th><th /></tr></thead>
        <tbody>
          {rates.map((r) => (
            <tr key={r.id}>
              <td>{r.province}</td>
              <td><input type="number" min="0" value={fees[r.id] ?? ''} onChange={(e) => setFees({ ...fees, [r.id]: e.target.value })} /></td>
              <td><button className="btn" onClick={() => save(r)}>Lưu</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
