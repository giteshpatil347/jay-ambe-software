export const MAX_FILE_BYTES = 10 * 1024 * 1024;
export const MAX_FILES = 10;
export const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'pdf'];
export const ALLOWED_MIME = ['image/jpeg', 'image/png', 'application/pdf'];

export function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/** Client-side mirror of server limits; returns a friendly message or null when OK. The server stays authoritative. */
export function validateFile(file: { name: string; size: number; type: string }, existingCount: number): string | null {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  if (!ALLOWED_EXT.includes(ext) || (file.type && !ALLOWED_MIME.includes(file.type))) {
    return `"${file.name}" is not allowed. Use JPG, PNG or PDF.`;
  }
  if (file.size > MAX_FILE_BYTES) return `"${file.name}" is ${humanSize(file.size)}; the limit is 10 MB.`;
  if (file.size === 0) return `"${file.name}" is empty.`;
  if (existingCount >= MAX_FILES) return `Maximum ${MAX_FILES} files per record.`;
  return null;
}
