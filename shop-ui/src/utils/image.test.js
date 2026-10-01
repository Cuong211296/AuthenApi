import { describe, expect, it } from 'vitest';
import { imageFileError, MAX_IMAGE_BYTES, optimizeImageUrl } from './image.js';

const BASE = 'https://res.cloudinary.com/demo/image/upload';

describe('optimizeImageUrl', () => {
  it('inserts the transformation after /upload/', () => {
    expect(optimizeImageUrl(`${BASE}/v1700000000/quinibear/products/abc.png`, 600))
      .toBe(`${BASE}/f_auto,q_auto,w_600,c_limit/v1700000000/quinibear/products/abc.png`);
  });
  it('works without a version segment', () => {
    expect(optimizeImageUrl(`${BASE}/sample.jpg`, 400)).toBe(`${BASE}/f_auto,q_auto,w_400,c_limit/sample.jpg`);
  });
  it('does not double-insert when a transformation already exists', () => {
    const done = `${BASE}/f_auto,q_auto,w_600,c_limit/v1/a.png`;
    expect(optimizeImageUrl(done, 300)).toBe(done);
    expect(optimizeImageUrl(`${BASE}/w_200/v1/a.png`, 300)).toBe(`${BASE}/w_200/v1/a.png`);
  });
  it('leaves other hosts, empty and invalid input unchanged', () => {
    const unsplash = 'https://images.unsplash.com/photo-1?w=800';
    expect(optimizeImageUrl(unsplash, 600)).toBe(unsplash);
    expect(optimizeImageUrl('http://res.cloudinary.com/demo/image/upload/a.png', 600)).toBe('http://res.cloudinary.com/demo/image/upload/a.png');
    expect(optimizeImageUrl('https://res.cloudinary.com/demo/video/upload/a.mp4', 600)).toBe('https://res.cloudinary.com/demo/video/upload/a.mp4');
    expect(optimizeImageUrl('', 600)).toBe('');
    expect(optimizeImageUrl(null, 600)).toBeNull();
    expect(optimizeImageUrl(undefined, 600)).toBeUndefined();
    expect(optimizeImageUrl('not a url', 600)).toBe('not a url');
  });
  it('ignores an unusable width', () => {
    expect(optimizeImageUrl(`${BASE}/a.png`, 0)).toBe(`${BASE}/a.png`);
    expect(optimizeImageUrl(`${BASE}/a.png`, NaN)).toBe(`${BASE}/a.png`);
  });
});

describe('imageFileError', () => {
  const file = (type, size) => ({ type, size });
  it('accepts jpeg, png and webp up to 5 MB', () => {
    for (const t of ['image/jpeg', 'image/png', 'image/webp']) expect(imageFileError(file(t, 1000))).toBe('');
    expect(imageFileError(file('image/png', MAX_IMAGE_BYTES))).toBe('');
  });
  it('rejects other types, oversize and empty files with Vietnamese messages', () => {
    expect(imageFileError(file('image/gif', 10))).toMatch(/JPEG, PNG hoặc WebP/);
    expect(imageFileError(file('application/pdf', 10))).toMatch(/JPEG, PNG hoặc WebP/);
    expect(imageFileError(file('image/png', MAX_IMAGE_BYTES + 1))).toMatch(/5 MB/);
    expect(imageFileError(file('image/png', 0))).toMatch(/trống/);
    expect(imageFileError(null)).toMatch(/Chưa chọn/);
  });
});
