const PHONE = /^(0|\+84)[0-9]{9}$/;

export function isValidPhone(phone) {
  return PHONE.test(phone);
}

/** Trims text fields (the server validates the raw values) and strips separators typed inside a phone number. */
export function normalizeCheckoutForm(form) {
  const note = (form.note || '').trim();
  return {
    ...form,
    receiverName: form.receiverName.trim(),
    phone: form.phone.replace(/[\s.-]/g, ''),
    email: form.email.trim(),
    address: form.address.trim(),
    note: note || null,
  };
}
