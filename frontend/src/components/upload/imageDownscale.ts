export const MAX_SIDE = 2000;
export const JPEG_QUALITY = 0.82;
/** Images at or below this size and dimensions are uploaded untouched. */
const KEEP_BYTES = 1.5 * 1024 * 1024;

const toJpegName = (name: string) => `${name.replace(/\.[^./\\]+$/, '') || 'photo'}.jpg`;

/**
 * Shrinks a camera photo before upload: longest side <= 2000 px, JPEG ~0.82, EXIF orientation applied.
 * PDFs and non-images are returned untouched; on any decoding/encoding failure the original file is returned.
 */
export async function downscaleImage(file: File): Promise<File> {
  if (!file.type.startsWith('image/') || typeof createImageBitmap !== 'function') return file;
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
    try {
      const scale = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height));
      if (scale === 1 && file.size <= KEEP_BYTES) return file;
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(bmp.width * scale));
      canvas.height = Math.max(1, Math.round(bmp.height * scale));
      const ctx = canvas.getContext('2d');
      if (!ctx) return file;
      ctx.fillStyle = '#fff'; // JPEG has no alpha
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(bmp, 0, 0, canvas.width, canvas.height);
      const blob = await new Promise<Blob | null>((res) => canvas.toBlob(res, 'image/jpeg', JPEG_QUALITY));
      if (!blob || (scale === 1 && blob.size >= file.size)) return file;
      return new File([blob], toJpegName(file.name), { type: 'image/jpeg', lastModified: file.lastModified });
    } finally { bmp.close?.(); }
  } catch {
    return file;
  }
}
