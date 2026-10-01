const CLOUDINARY = /^(https:\/\/res\.cloudinary\.com\/[^/?#]+\/image\/upload\/)(.+)$/;
// A transformation segment is comma separated "k_v" params or a single one such as w_300 / f_auto.
const TRANSFORMATION = /^(?:[a-z]{1,3}_[^/,]+)(?:,[a-z]{1,3}_[^/,]+)*$/;

/**
 * Asks Cloudinary for an automatically formatted / compressed copy no wider than `width` px.
 * Only https://res.cloudinary.com/<cloud>/image/upload/<rest> URLs are touched; anything else (Unsplash, empty,
 * invalid) is returned unchanged, as is a Cloudinary URL that already carries a transformation segment.
 */
export function optimizeImageUrl(url, width) {
  if (typeof url !== 'string') return url;
  const m = CLOUDINARY.exec(url.trim());
  if (!m) return url;
  const w = Math.round(Number(width));
  if (!Number.isFinite(w) || w <= 0) return url;
  const [first] = m[2].split('/');
  if (TRANSFORMATION.test(first)) return url;
  return `${m[1]}f_auto,q_auto,w_${w},c_limit/${m[2]}`;
}

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/** Client-side pre-check for an upload; returns a Vietnamese message, or '' when the file looks acceptable. */
export function imageFileError(file) {
  if (!file) return 'Chưa chọn ảnh';
  if (!ALLOWED_TYPES.includes(file.type)) return 'Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP';
  if (file.size > MAX_IMAGE_BYTES) return 'Ảnh vượt quá dung lượng tối đa 5 MB';
  if (file.size === 0) return 'Tệp ảnh trống';
  return '';
}
