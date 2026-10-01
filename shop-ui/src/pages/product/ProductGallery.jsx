import { useRef, useState } from 'react';
import Modal from '../../components/ui/Modal.jsx';
import { optimizeImageUrl } from '../../utils/image.js';
import { initialsOf } from '../../utils/initials.js';

/**
 * Product image with hover zoom-pan (mouse only, follows the pointer) and click-to-open lightbox.
 * Falls back to a gradient with the product initials when there is no image or it fails to load.
 */
export default function ProductGallery({ src, name }) {
  const [failed, setFailed] = useState(false);
  const [open, setOpen] = useState(false);
  const frameRef = useRef(null);
  const showImage = src && !failed;

  function pan(e) {
    if (e.pointerType !== 'mouse') return;
    const el = frameRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    el.style.setProperty('--zx', `${((e.clientX - r.left) / r.width) * 100}%`);
    el.style.setProperty('--zy', `${((e.clientY - r.top) / r.height) * 100}%`);
  }

  if (!showImage) {
    return (
      <div className="pg__frame pg__frame--fallback" role="img" aria-label={`${name}, chưa có ảnh`}>
        <span aria-hidden="true">{initialsOf(name)}</span>
      </div>
    );
  }

  return (
    <>
      <button
        ref={frameRef}
        type="button"
        className="pg__frame pg__frame--zoom"
        aria-label={`Xem ảnh lớn: ${name}`}
        aria-haspopup="dialog"
        onPointerMove={pan}
        onClick={() => setOpen(true)}
      >
        <img src={optimizeImageUrl(src, 1200)} alt={name} decoding="async" fetchpriority="high" onError={() => setFailed(true)} />
        <span className="pg__hint" aria-hidden="true">Nhấn để phóng to</span>
      </button>
      <Modal open={open} onClose={() => setOpen(false)} title={name} hideTitle variant="media">
        <img src={optimizeImageUrl(src, 1600)} alt={name} />
      </Modal>
    </>
  );
}
