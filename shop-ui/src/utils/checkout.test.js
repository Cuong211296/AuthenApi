import { normalizeCheckoutForm, isValidPhone, isValidEmail, checkoutErrors } from './checkout.js';

const base = {
  receiverName: ' Test A ',
  phone: ' 0901234567 ',
  email: ' a@b.com ',
  ward: ' Phường 14 ',
  address: ' 339 Lê Văn Sỹ ',
  province: 'TP Hồ Chí Minh',
  note: '   ',
  paymentMethod: 'MOMO',
};

describe('normalizeCheckoutForm', () => {
  it('trims every text field so the server sees exactly what was validated', () => {
    const out = normalizeCheckoutForm(base);
    expect(out.receiverName).toBe('Test A');
    expect(out.phone).toBe('0901234567');
    expect(out.email).toBe('a@b.com');
    expect(out.ward).toBe('Phường 14');
    expect(out.address).toBe('339 Lê Văn Sỹ');
  });

  it('sends a blank note as null', () => {
    expect(normalizeCheckoutForm(base).note).toBeNull();
    expect(normalizeCheckoutForm({ ...base, note: ' giao giờ hành chính ' }).note).toBe('giao giờ hành chính');
  });

  it('removes spaces, dots and dashes typed inside a phone number', () => {
    expect(normalizeCheckoutForm({ ...base, phone: '090 123.45-67' }).phone).toBe('0901234567');
    expect(normalizeCheckoutForm({ ...base, phone: '+84 901234567' }).phone).toBe('+84901234567');
  });

  it('keeps the payment method and province untouched', () => {
    const out = normalizeCheckoutForm(base);
    expect(out.paymentMethod).toBe('MOMO');
    expect(out.province).toBe('TP Hồ Chí Minh');
  });
});

describe('isValidPhone', () => {
  it('accepts 0xxxxxxxxx and +84xxxxxxxxx', () => {
    expect(isValidPhone('0901234567')).toBe(true);
    expect(isValidPhone('+84901234567')).toBe(true);
  });
  it('rejects card numbers, short numbers and letters', () => {
    expect(isValidPhone('9704 0000 0000 0018')).toBe(false);
    expect(isValidPhone('090123')).toBe(false);
    expect(isValidPhone('09012345ab')).toBe(false);
  });
});

describe('checkoutErrors', () => {
  const ok = { ...base, receiverName: 'A', phone: '0901234567', email: 'a@b.com', ward: 'P1', address: 'x', province: 'Hà Nội' };

  it('returns no errors for a valid form (values are trimmed first)', () => {
    expect(checkoutErrors(base)).toEqual({});
    expect(checkoutErrors(ok)).toEqual({});
  });

  it('flags every empty required field', () => {
    const e = checkoutErrors({ ...ok, receiverName: ' ', phone: '', email: '', ward: ' ', address: '  ', province: '' });
    expect(Object.keys(e).sort()).toEqual(['address', 'email', 'phone', 'province', 'receiverName', 'ward']);
  });

  it('requires the ward with a Vietnamese message', () => {
    expect(checkoutErrors({ ...ok, ward: '   ' }).ward).toBe('Vui lòng nhập phường/xã');
    expect(normalizeCheckoutForm({ ...ok, ward: undefined }).ward).toBe('');
  });

  it('distinguishes a missing phone from an invalid one', () => {
    expect(checkoutErrors({ ...ok, phone: '' }).phone).toMatch(/nhập/);
    expect(checkoutErrors({ ...ok, phone: '12345' }).phone).toMatch(/không hợp lệ/);
  });

  it('validates the email shape', () => {
    expect(checkoutErrors({ ...ok, email: 'abc' }).email).toMatch(/không hợp lệ/);
    expect(checkoutErrors({ ...ok, email: 'a@b' }).email).toMatch(/không hợp lệ/);
    expect(isValidEmail('ten@example.com')).toBe(true);
  });
});
